package com.edreams.flights.infrastructure.json;

import java.math.BigDecimal;
import java.util.List;

import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.ProviderId;

/** API and cache representation of the aggregates over a filtered search. */
public record StatsJson(
        long total,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<BucketJson> priceBuckets,
        List<CarrierJson> byCarrier,
        List<ProviderJson> byProvider,
        List<StopsJson> byStops) {

    public record BucketJson(BigDecimal from, BigDecimal to, long count) {
    }

    public record CarrierJson(String carrier, long count, BigDecimal minPrice) {
    }

    public record ProviderJson(String provider, long count) {
    }

    public record StopsJson(int stops, long count) {
    }

    public static StatsJson from(SearchStats s) {
        return new StatsJson(s.total(), s.minPrice(), s.maxPrice(),
                s.priceBuckets().stream().map(b -> new BucketJson(b.from(), b.to(), b.count())).toList(),
                s.byCarrier().stream().map(c -> new CarrierJson(c.carrier().code(), c.count(), c.minPrice())).toList(),
                s.byProvider().stream().map(p -> new ProviderJson(p.provider().name(), p.count())).toList(),
                s.byStops().stream().map(st -> new StopsJson(st.stops(), st.count())).toList());
    }

    public SearchStats toStats() {
        return new SearchStats(total, minPrice, maxPrice,
                priceBuckets.stream().map(b -> new SearchStats.PriceBucket(b.from(), b.to(), b.count())).toList(),
                byCarrier.stream().map(c -> new SearchStats.CarrierCount(new Carrier(c.carrier()), c.count(), c.minPrice())).toList(),
                byProvider.stream().map(p -> new SearchStats.ProviderCount(ProviderId.valueOf(p.provider()), p.count())).toList(),
                byStops.stream().map(st -> new SearchStats.StopsCount(st.stops(), st.count())).toList());
    }
}
