package com.edreams.flights.infrastructure.persistence;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.out.ProviderOfferRepository;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.infrastructure.json.JsonCodec;

/**
 * Per-run provider offers. Inserts are batched and idempotent; the ordered read streams through a
 * server-side cursor (fetch size inside a read-only transaction), so a run of two million rows is
 * resolved with constant memory.
 */
@Repository
public class JdbcProviderOfferRepository implements ProviderOfferRepository {

    private static final String INSERT = """
            INSERT INTO provider_offer (signature, provider, provider_offer_id, price, currency, fetched_at, ingestion_run_id, segments)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (ingestion_run_id, signature, provider) DO NOTHING
            """;
    private static final String SELECT_ORDERED = """
            SELECT provider, provider_offer_id, price, currency, segments
            FROM provider_offer
            WHERE ingestion_run_id = ?
            ORDER BY signature, provider
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate streaming;
    private final TransactionTemplate readOnly;
    private final JsonCodec json;

    public JdbcProviderOfferRepository(DataSource dataSource, PlatformTransactionManager transactionManager, JsonCodec json) {
        this.jdbc = JdbcClient.create(dataSource);
        this.streaming = new JdbcTemplate(dataSource);
        this.streaming.setFetchSize(5_000);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.json = json;
    }

    @Override
    public void insertAll(IngestionRunId run, List<ProviderOffer> offers) {
        if (offers.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Object[]> rows = offers.stream().map(o -> new Object[] {
                o.signature().value(), o.provider().name(), o.providerOfferId(), o.price().amount(),
                o.price().currency().getCurrencyCode(), now, run.value(), json.writeSegments(o.segments())
        }).toList();
        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.CHAR, Types.TIMESTAMP_WITH_TIMEZONE,
                Types.OTHER, Types.VARCHAR};
        streaming.batchUpdate(INSERT, rows, types);
    }

    @Override
    public void deleteForRun(IngestionRunId run, ProviderId provider) {
        jdbc.sql("DELETE FROM provider_offer WHERE ingestion_run_id = :run AND provider = :provider")
                .param("run", run.value()).param("provider", provider.name()).update();
    }

    @Override
    public void forEachOfRunOrderedBySignature(IngestionRunId run, Consumer<ProviderOffer> consumer) {
        readOnly.executeWithoutResult(status -> streaming.query(SELECT_ORDERED, rs -> {
            consumer.accept(new ProviderOffer(
                    ProviderId.valueOf(rs.getString("provider")),
                    rs.getString("provider_offer_id"),
                    json.readSegments(rs.getString("segments")),
                    new Money(rs.getBigDecimal("price"), java.util.Currency.getInstance(rs.getString("currency").trim()))));
        }, run.value()));
    }

    @Override
    public int purgeRunsNotIn(Collection<IngestionRunId> keep) {
        List<UUID> ids = keep.stream().map(IngestionRunId::value).toList();
        return jdbc.sql("DELETE FROM provider_offer WHERE ingestion_run_id NOT IN (:keep)").param("keep", ids).update();
    }
}
