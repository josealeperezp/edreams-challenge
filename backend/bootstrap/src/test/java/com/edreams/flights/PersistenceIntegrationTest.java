package com.edreams.flights;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.ingestion.ProviderResult;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.ProviderOfferRepository;
import com.edreams.flights.application.search.Cursor;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.ProviderQuote;
import com.edreams.flights.domain.model.Segment;
import com.edreams.flights.domain.rule.RejectionReason;

class PersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final OffsetDateTime DEP = OffsetDateTime.parse("2026-11-02T07:15:00+01:00");

    @Autowired
    private ProviderOfferRepository offers;

    @Autowired
    private ItineraryRepository itineraries;

    @Autowired
    private IngestionRunRepository runs;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE provider_offer, itinerary, ingestion_run").update();
    }

    private static Segment segment(String carrier, int number, String from, String to, int slot) {
        return new Segment(new Carrier(carrier), new FlightNumber(number), new AirportCode(from), new AirportCode(to),
                DEP.plusHours(slot), DEP.plusHours(slot).plusMinutes(80));
    }

    private static ProviderOffer offer(ProviderId provider, Segment segment, String price) {
        return new ProviderOffer(provider, provider.name() + "-" + segment.flightCode(), List.of(segment), Money.eur(price));
    }

    private static Itinerary itinerary(Segment segment, String price, ProviderId best) {
        Money base = Money.eur(price);
        return new Itinerary(ItinerarySignature.of(List.of(segment)), List.of(segment), base,
                base.plusPercent(BigDecimal.valueOf(5)), best, List.of(new ProviderQuote(best, base)));
    }

    /** 40 direct flights from MAD: 20 to BCN and 20 to LHR, carriers IB (14), VY (13), BA (13), prices 50 to 72. */
    private static List<Itinerary> fortyFromMadrid() {
        List<Itinerary> seed = new ArrayList<>();
        String[] carriers = {"IB", "VY", "BA"};
        for (int i = 0; i < 40; i++) {
            String to = i % 2 == 0 ? "BCN" : "LHR";
            Segment s = segment(carriers[i % 3], 100 + i, "MAD", to, i % 7);
            seed.add(itinerary(s, String.valueOf(50 + (i * 7) % 23), ProviderId.values()[i % 3]));
        }
        return seed;
    }

    private IngestionRunId run() {
        IngestionRunId id = IngestionRunId.newId();
        runs.save(IngestionRun.started(id, Instant.now()));
        return id;
    }

    @Test
    void offersAreIdempotentPerRunAndStreamGroupedBySignature() {
        IngestionRunId run = run();
        Segment shared = segment("IB", 845, "MAD", "BCN", 0);
        Segment alphaOnly = segment("VY", 1002, "BCN", "LHR", 1);
        offers.insertAll(run, List.of(offer(ProviderId.ALPHA, shared, "89.90"), offer(ProviderId.ALPHA, alphaOnly, "70")));
        offers.insertAll(run, List.of(offer(ProviderId.ALPHA, shared, "89.90")));
        offers.insertAll(run, List.of(offer(ProviderId.BETA, shared, "85.50")));

        List<ProviderOffer> streamed = new ArrayList<>();
        offers.forEachOfRunOrderedBySignature(run, streamed::add);

        assertThat(streamed).hasSize(3);
        assertThat(streamed.get(0).signature()).isEqualTo(streamed.get(1).signature());
        assertThat(streamed.get(0).provider()).isEqualTo(ProviderId.ALPHA);
        assertThat(streamed.get(1).provider()).isEqualTo(ProviderId.BETA);
        assertThat(streamed.get(1).price()).isEqualTo(Money.eur("85.50"));
        assertThat(streamed.get(1).segments().getFirst().departure()).isEqualTo(DEP);

        offers.deleteForRun(run, ProviderId.BETA);
        List<ProviderOffer> afterDelete = new ArrayList<>();
        offers.forEachOfRunOrderedBySignature(run, afterDelete::add);
        assertThat(afterDelete).extracting(ProviderOffer::provider).containsOnly(ProviderId.ALPHA);

        IngestionRunId newer = run();
        offers.insertAll(newer, List.of(offer(ProviderId.GAMMA, shared, "92")));
        assertThat(offers.purgeRunsNotIn(List.of(newer))).isEqualTo(2);
    }

    @Test
    void keysetPagesCoverEveryRowOnceInPriceOrderUnderEveryFilter() {
        IngestionRunId run = run();
        itineraries.upsertAll(run, fortyFromMadrid());
        assertThat(itineraries.approximateCount()).isEqualTo(40);

        List<ItineraryRow> all = walk(SearchQuery.of("MAD", null, null, null, null, 7, 100));
        assertThat(all).hasSize(40);
        Set<Long> ids = new HashSet<>();
        ItineraryRow previous = null;
        for (ItineraryRow row : all) {
            assertThat(ids.add(row.id())).as("no duplicate across pages").isTrue();
            if (previous != null) {
                int byPrice = row.itinerary().price().compareAmount(previous.itinerary().price());
                assertThat(byPrice > 0 || (byPrice == 0 && row.id() > previous.id())).as("price, id order").isTrue();
            }
            previous = row;
        }

        assertThat(walk(SearchQuery.of("MAD", "LHR", null, null, null, 6, 100))).hasSize(20)
                .allSatisfy(r -> assertThat(r.itinerary().destination().value()).isEqualTo("LHR"));
        assertThat(walk(SearchQuery.of(null, null, null, "VY", null, 5, 100))).hasSize(13)
                .allSatisfy(r -> assertThat(r.itinerary().carrier().code()).isEqualTo("VY"));
        List<ItineraryRow> cheap = walk(SearchQuery.of("MAD", null, new BigDecimal("60"), null, null, 50, 100));
        assertThat(cheap).isNotEmpty().allSatisfy(r -> assertThat(r.itinerary().price().amount()).isLessThanOrEqualTo(new BigDecimal("60")));
        assertThat(walk(SearchQuery.of("BCN", null, null, null, null, 5, 100))).isEmpty();
    }

    @Test
    void statsDescribeTheWholeFilteredSetNotAPage() {
        IngestionRunId run = run();
        Segment single = segment("IB", 845, "MAD", "BCN", 0);
        itineraries.upsertAll(run, List.of(itinerary(single, "85.50", ProviderId.BETA)));

        SearchStats one = itineraries.stats(SearchQuery.of("MAD", "BCN", null, null, null, null, 100));
        assertThat(one.total()).isEqualTo(1);
        assertThat(one.minPrice()).isEqualByComparingTo("89.78");
        assertThat(one.maxPrice()).isEqualByComparingTo("89.78");
        assertThat(one.priceBuckets()).containsExactly(new SearchStats.PriceBucket(new BigDecimal("89.78"), new BigDecimal("89.78"), 1));
        assertThat(one.byProvider()).containsExactly(new SearchStats.ProviderCount(ProviderId.BETA, 1));

        itineraries.upsertAll(run, fortyFromMadrid());
        SearchStats all = itineraries.stats(SearchQuery.of("MAD", null, null, null, null, 3, 100));
        assertThat(all.total()).isEqualTo(41);
        assertThat(all.priceBuckets()).hasSize(SearchStats.BUCKETS);
        assertThat(all.priceBuckets().stream().mapToLong(SearchStats.PriceBucket::count).sum()).isEqualTo(41);
        assertThat(all.priceBuckets().getFirst().from()).isEqualByComparingTo(all.minPrice());
        assertThat(all.priceBuckets().getLast().to()).isEqualByComparingTo(all.maxPrice());
        assertThat(all.byProvider().stream().mapToLong(SearchStats.ProviderCount::count).sum()).isEqualTo(41);
        assertThat(all.byCarrier()).extracting(c -> c.carrier().code()).containsExactly("IB", "BA", "VY");
        assertThat(all.byCarrier().getFirst().count()).isEqualTo(15);
        assertThat(all.byCarrier().getFirst().minPrice()).isEqualByComparingTo(all.minPrice());
        assertThat(all.byStops()).containsExactly(new SearchStats.StopsCount(0, 41));

        assertThat(itineraries.stats(SearchQuery.of("MAD", "LHR", null, null, null, null, 100)).total()).isEqualTo(20);
        List<ItineraryRow> cheapVueling = walk(SearchQuery.of("MAD", null, new BigDecimal("60"), "VY", null, 50, 100));
        SearchStats cheapStats = itineraries.stats(SearchQuery.of("MAD", null, new BigDecimal("60"), "VY", null, null, 100));
        assertThat(cheapStats.total()).isEqualTo(cheapVueling.size()).isPositive();
        assertThat(cheapStats.minPrice()).isEqualByComparingTo(cheapVueling.getFirst().itinerary().price().amount());
        assertThat(cheapStats.byCarrier()).extracting(c -> c.carrier().code()).containsExactly("VY");

        SearchStats none = itineraries.stats(SearchQuery.of("BCN", null, null, null, null, null, 100));
        assertThat(none.total()).isZero();
        assertThat(none.minPrice()).isNull();
        assertThat(none.priceBuckets()).isEmpty();
        assertThat(none.byProvider()).isEmpty();
    }

    @Test
    void upsertReplacesTheWinnerAndPurgeDropsUnconfirmedRows() {
        IngestionRunId first = run();
        Segment shared = segment("IB", 845, "MAD", "BCN", 0);
        Segment old = segment("AF", 1801, "MAD", "CDG", 2);
        itineraries.upsertAll(first, List.of(itinerary(shared, "89.90", ProviderId.ALPHA), itinerary(old, "120", ProviderId.BETA)));

        IngestionRunId second = run();
        itineraries.upsertAll(second, List.of(itinerary(shared, "85.50", ProviderId.BETA)));

        List<ItineraryRow> rows = walk(SearchQuery.of("MAD", "BCN", null, null, null, 10, 100));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().itinerary().bestProvider()).isEqualTo(ProviderId.BETA);
        assertThat(rows.getFirst().itinerary().price()).isEqualTo(Money.eur("89.78"));
        assertThat(rows.getFirst().confirmedBy()).isEqualTo(second);
        assertThat(itineraries.approximateCount()).isEqualTo(2);

        assertThat(itineraries.purgeNotConfirmedBy(List.of(second))).isEqualTo(1);
        assertThat(itineraries.approximateCount()).isEqualTo(1);
    }

    @Test
    void runsRoundTripWithProviderResults() {
        IngestionRunId id = run();
        IngestionRun run = runs.find(id).orElseThrow();
        assertThat(run.status()).isEqualTo(IngestionRun.Status.RUNNING);

        run = run.withProvider(ProviderId.BETA, ProviderResult.ok(1234, 10, 9, Map.of(RejectionReason.DEAD_LEG, 1L)))
                .withProvider(ProviderId.GAMMA, ProviderResult.failed(5000, 0, 0, Map.of(), "ProviderCallException: GAMMA responded HTTP 500"))
                .finished(IngestionRun.Status.PARTIAL, 9, Instant.now().plus(Duration.ofSeconds(5)));
        runs.save(run);

        IngestionRun stored = runs.latest().orElseThrow();
        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.status()).isEqualTo(IngestionRun.Status.PARTIAL);
        assertThat(stored.itineraries()).isEqualTo(9);
        assertThat(stored.providers().get(ProviderId.BETA).rejected()).containsEntry(RejectionReason.DEAD_LEG, 1L);
        assertThat(stored.providers().get(ProviderId.GAMMA).error()).contains("HTTP 500");
        assertThat(runs.latest(5)).hasSize(1);
    }

    private List<ItineraryRow> walk(SearchQuery first) {
        List<ItineraryRow> all = new ArrayList<>();
        SearchQuery query = first;
        for (int page = 0; page < 100; page++) {
            List<ItineraryRow> rows = itineraries.search(query, query.limit() + 1);
            boolean hasNext = rows.size() > query.limit();
            List<ItineraryRow> items = hasNext ? rows.subList(0, query.limit()) : rows;
            all.addAll(items);
            if (!hasNext) {
                return all;
            }
            query = new SearchQuery(query.origin(), query.destination(), query.maxPrice(), query.carrier(),
                    Cursor.after(items.getLast()), query.limit());
        }
        throw new AssertionError("pagination did not terminate");
    }
}
