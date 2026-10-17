package com.edreams.flights;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderQuote;
import com.edreams.flights.domain.model.Segment;

class RedisSearchCacheTest extends AbstractIntegrationTest {

    @Autowired
    private SearchCache cache;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void versionIncrementsAndPagesRoundTripWithATtl() {
        long before = cache.currentVersion();
        assertThat(cache.bumpVersion()).isEqualTo(before + 1);
        assertThat(cache.currentVersion()).isEqualTo(before + 1);

        Segment segment = new Segment(new Carrier("IB"), new FlightNumber(845), new AirportCode("MAD"), new AirportCode("BCN"),
                OffsetDateTime.parse("2026-11-02T07:15:00+01:00"), OffsetDateTime.parse("2026-11-02T08:35:00+01:00"));
        Itinerary itinerary = new Itinerary(ItinerarySignature.of(List.of(segment)), List.of(segment), Money.eur("85.50"),
                Money.eur("89.78"), ProviderId.BETA, List.of(new ProviderQuote(ProviderId.BETA, Money.eur("85.50"))));
        ItineraryRow row = new ItineraryRow(42L, itinerary, IngestionRunId.newId(), Instant.parse("2026-10-17T10:00:00Z"));
        SearchPage page = new SearchPage(List.of(row), "next", true, new SearchPage.Inventory(before + 1, 1, null));

        String key = (before + 1) + ":MAD|BCN|||" + "|20";
        cache.put(key, page);
        Optional<SearchPage> cached = cache.get(key);

        assertThat(cached).isPresent();
        assertThat(cached.get().items().getFirst().id()).isEqualTo(42L);
        assertThat(cached.get().items().getFirst().itinerary()).isEqualTo(itinerary);
        assertThat(cached.get().items().getFirst().confirmedBy()).isEqualTo(row.confirmedBy());
        assertThat(cached.get().nextCursor()).isEqualTo("next");
        assertThat(cache.get("other")).isEmpty();
        assertThat(redis.keys("search:*")).isNotEmpty()
                .allSatisfy(k -> assertThat(redis.getExpire(k)).isBetween(1L, 60L));
    }

    @Test
    void statsRoundTripUnderTheirOwnPrefixWithATtl() {
        SearchStats stats = new SearchStats(2, new BigDecimal("80.00"), new BigDecimal("90.00"),
                List.of(new SearchStats.PriceBucket(new BigDecimal("80.00"), new BigDecimal("90.00"), 2)),
                List.of(new SearchStats.CarrierCount(new Carrier("IB"), 2, new BigDecimal("80.00"))),
                List.of(new SearchStats.ProviderCount(ProviderId.BETA, 2)), List.of(new SearchStats.StopsCount(0, 2)));
        String key = cache.currentVersion() + ":stats:MAD|BCN||";

        cache.putStats(key, stats);

        assertThat(cache.getStats(key)).contains(stats);
        assertThat(cache.getStats("missing")).isEmpty();
        assertThat(redis.keys("stats:*")).isNotEmpty()
                .allSatisfy(k -> assertThat(redis.getExpire(k)).isBetween(1L, 60L));
    }
}
