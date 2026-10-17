package com.edreams.flights.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.search.Cursor;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.infrastructure.json.JsonCodec;

/**
 * The search index. One row per signature, upserted by the resolve phase, read by keyset: the
 * filters and the {@code (price, id) > (:price, :id)} cursor predicate are served by the composite
 * indexes declared in the migrations, so page depth never changes the cost. Stats run the same
 * filters once through grouping sets and are cached per inventory version by the service.
 */
@Repository
public class JdbcItineraryRepository implements ItineraryRepository {

    private static final String UPSERT = """
            INSERT INTO itinerary (signature, origin, destination, carrier, departure_at, arrival_at, stops,
                                   base_price, price, currency, best_provider, segments, quotes, ingestion_run_id, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            ON CONFLICT (signature) DO UPDATE SET
                origin = EXCLUDED.origin, destination = EXCLUDED.destination, carrier = EXCLUDED.carrier,
                departure_at = EXCLUDED.departure_at, arrival_at = EXCLUDED.arrival_at, stops = EXCLUDED.stops,
                base_price = EXCLUDED.base_price, price = EXCLUDED.price, currency = EXCLUDED.currency,
                best_provider = EXCLUDED.best_provider, segments = EXCLUDED.segments, quotes = EXCLUDED.quotes,
                ingestion_run_id = EXCLUDED.ingestion_run_id, updated_at = EXCLUDED.updated_at
            """;
    private static final String SELECT = """
            SELECT id, signature, base_price, price, currency, best_provider, segments, quotes, ingestion_run_id, updated_at
            FROM itinerary
            """;
    private static final String BOUNDS = "SELECT count(*) AS total, min(price) AS lo, max(price) AS hi FROM itinerary";
    /**
     * One hash aggregate over the filtered rows on all four group columns at once (at most a few
     * thousand groups); the four marginals are rolled up in Java. GROUPING SETS would answer in one
     * statement too, but at a million rows the planner sorts the whole table on disk for them.
     */
    private static final String GROUPS = """
            SELECT carrier, best_provider, stops, bucket, count(*) AS n, min(price) AS min_price
            FROM (SELECT carrier, best_provider, stops, price, width_bucket(price, ?, ?, %d) AS bucket
                  FROM itinerary%s) AS filtered
            GROUP BY carrier, best_provider, stops, bucket
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final TransactionTemplate ownConnection;
    private final JsonCodec json;

    public JdbcItineraryRepository(DataSource dataSource, PlatformTransactionManager transactionManager, JsonCodec json) {
        this.jdbc = JdbcClient.create(dataSource);
        this.batch = new JdbcTemplate(dataSource);
        // The resolve phase calls upsertAll from inside the read-only streaming transaction of
        // provider_offer. REQUIRES_NEW suspends that transaction and writes on a second pooled
        // connection, so the cursor stays open and the write is not rejected as read-only.
        this.ownConnection = new TransactionTemplate(transactionManager);
        this.ownConnection.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
    }

    @Override
    public void upsertAll(IngestionRunId run, List<Itinerary> itineraries) {
        if (itineraries.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Object[]> rows = itineraries.stream().map(it -> new Object[] {
                it.signature().value(), it.origin().value(), it.destination().value(), it.carrier().code(),
                it.departureAt(), it.arrivalAt(), it.stops(),
                it.basePrice().amount(), it.price().amount(), it.price().currency().getCurrencyCode(), it.bestProvider().name(),
                json.writeSegments(it.segments()), json.writeQuotes(it.quotes()), run.value(), now
        }).toList();
        int[] types = {Types.VARCHAR, Types.CHAR, Types.CHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE,
                Types.TIMESTAMP_WITH_TIMEZONE, Types.SMALLINT, Types.NUMERIC, Types.NUMERIC, Types.CHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.OTHER, Types.TIMESTAMP_WITH_TIMEZONE};
        ownConnection.executeWithoutResult(tx -> batch.batchUpdate(UPSERT, rows, types));
    }

    @Override
    public int purgeNotConfirmedBy(Collection<IngestionRunId> keep) {
        List<UUID> ids = keep.stream().map(IngestionRunId::value).toList();
        return jdbc.sql("DELETE FROM itinerary WHERE ingestion_run_id NOT IN (:keep)").param("keep", ids).update();
    }

    @Override
    public List<ItineraryRow> search(SearchQuery query, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT).append(where(query, params));
        Cursor cursor = query.cursor();
        if (cursor != null) {
            sql.append(" AND (price, id) > (?, ?)");
            params.add(cursor.price());
            params.add(cursor.id());
        }
        sql.append(" ORDER BY price, id LIMIT ?");
        params.add(limit);
        return batch.query(sql.toString(), (rs, i) -> toRow(
                rs.getLong("id"), rs.getString("signature"), rs.getBigDecimal("base_price"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getString("best_provider"), rs.getString("segments"), rs.getString("quotes"),
                rs.getObject("ingestion_run_id", UUID.class), rs.getTimestamp("updated_at").toInstant()), params.toArray());
    }

    @Override
    public SearchStats stats(SearchQuery query) {
        List<Object> params = new ArrayList<>();
        String where = where(query, params);
        Bounds bounds = batch.query(BOUNDS + where, (ResultSetExtractor<Bounds>) rs -> rs.next()
                ? new Bounds(rs.getLong("total"), rs.getBigDecimal("lo"), rs.getBigDecimal("hi"))
                : new Bounds(0, null, null), params.toArray());
        if (bounds.total() == 0) {
            return SearchStats.empty();
        }
        // width_bucket rejects an empty range; with a single price every row lands in bucket 1.
        BigDecimal upper = bounds.hi().compareTo(bounds.lo()) == 0 ? bounds.lo().add(new BigDecimal("0.01")) : bounds.hi();
        List<Object> groupParams = new ArrayList<>();
        groupParams.add(bounds.lo());
        groupParams.add(upper);
        groupParams.addAll(params);

        Map<String, long[]> carrierCounts = new HashMap<>();
        Map<String, BigDecimal> carrierMin = new HashMap<>();
        Map<String, Long> providerCounts = new HashMap<>();
        Map<Integer, Long> stopsCounts = new HashMap<>();
        Map<Integer, Long> buckets = new HashMap<>();
        batch.query(GROUPS.formatted(SearchStats.BUCKETS, where), (RowCallbackHandler) rs -> {
            long n = rs.getLong("n");
            String carrier = rs.getString("carrier").trim();
            carrierCounts.computeIfAbsent(carrier, k -> new long[1])[0] += n;
            carrierMin.merge(carrier, rs.getBigDecimal("min_price"), BigDecimal::min);
            providerCounts.merge(rs.getString("best_provider"), n, Long::sum);
            stopsCounts.merge(rs.getInt("stops"), n, Long::sum);
            buckets.merge(rs.getInt("bucket"), n, Long::sum);
        }, groupParams.toArray());

        List<SearchStats.CarrierCount> carriers = new ArrayList<>();
        carrierCounts.forEach((code, count) -> carriers.add(new SearchStats.CarrierCount(new Carrier(code), count[0], carrierMin.get(code))));
        carriers.sort(Comparator.comparingLong(SearchStats.CarrierCount::count).reversed()
                .thenComparing(c -> c.carrier().code()));
        List<SearchStats.ProviderCount> providers = new ArrayList<>();
        providerCounts.forEach((name, count) -> providers.add(new SearchStats.ProviderCount(ProviderId.valueOf(name), count)));
        providers.sort(Comparator.comparing(p -> p.provider().ordinal()));
        List<SearchStats.StopsCount> stops = new ArrayList<>();
        stopsCounts.forEach((value, count) -> stops.add(new SearchStats.StopsCount(value, count)));
        stops.sort(Comparator.comparingInt(SearchStats.StopsCount::stops));
        return new SearchStats(bounds.total(), bounds.lo(), bounds.hi(),
                SearchStats.buckets(bounds.lo(), bounds.hi(), buckets),
                carriers.subList(0, Math.min(SearchStats.TOP_CARRIERS, carriers.size())), providers, stops);
    }

    @Override
    public long approximateCount() {
        Long count = jdbc.sql("SELECT count(*) FROM itinerary").query(Long.class).single();
        return count == null ? 0 : count;
    }

    private record Bounds(long total, BigDecimal lo, BigDecimal hi) {
    }

    /** The filter predicate shared by pages and stats; the cursor is appended by {@code search} only. */
    private static String where(SearchQuery query, List<Object> params) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (query.origin() != null) {
            sql.append(" AND origin = ?");
            params.add(query.origin().value());
        }
        if (query.destination() != null) {
            sql.append(" AND destination = ?");
            params.add(query.destination().value());
        }
        if (query.carrier() != null) {
            sql.append(" AND carrier = ?");
            params.add(query.carrier().code());
        }
        if (query.maxPrice() != null) {
            sql.append(" AND price <= ?");
            params.add(query.maxPrice().amount());
        }
        return sql.toString();
    }

    private ItineraryRow toRow(long id, String signature, java.math.BigDecimal basePrice, java.math.BigDecimal price,
            String currency, String bestProvider, String segments, String quotes, UUID run, Instant updatedAt) {
        java.util.Currency cur = java.util.Currency.getInstance(currency.trim());
        Itinerary itinerary = new Itinerary(new ItinerarySignature(signature), json.readSegments(segments),
                new Money(basePrice, cur), new Money(price, cur), ProviderId.valueOf(bestProvider), json.readQuotes(quotes));
        return new ItineraryRow(id, itinerary, new IngestionRunId(run), updatedAt);
    }
}
