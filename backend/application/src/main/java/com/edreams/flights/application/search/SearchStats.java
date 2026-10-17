package com.edreams.flights.application.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.ProviderId;

/**
 * Aggregates over everything a search's filters match, not over one page: the dashboard charts
 * describe the whole filtered inventory. Computed by the repository in one pass and cached per
 * inventory version like pages.
 */
public record SearchStats(
        long total,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<PriceBucket> priceBuckets,
        List<CarrierCount> byCarrier,
        List<ProviderCount> byProvider,
        List<StopsCount> byStops) {

    /** Equal-width price buckets between the cheapest and the most expensive match. */
    public static final int BUCKETS = 20;
    public static final int TOP_CARRIERS = 10;

    public record PriceBucket(BigDecimal from, BigDecimal to, long count) {
    }

    public record CarrierCount(Carrier carrier, long count, BigDecimal minPrice) {
    }

    public record ProviderCount(ProviderId provider, long count) {
    }

    public record StopsCount(int stops, long count) {
    }

    public SearchStats {
        priceBuckets = priceBuckets == null ? List.of() : List.copyOf(priceBuckets);
        byCarrier = byCarrier == null ? List.of() : List.copyOf(byCarrier);
        byProvider = byProvider == null ? List.of() : List.copyOf(byProvider);
        byStops = byStops == null ? List.of() : List.copyOf(byStops);
    }

    public static SearchStats empty() {
        return new SearchStats(0, null, null, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Turns bucket indexes as PostgreSQL's {@code width_bucket(price, min, max, BUCKETS)} numbers
     * them (1..BUCKETS, BUCKETS + 1 for a price equal to max) into labelled ranges. A set with one
     * price collapses to one bucket.
     */
    public static List<PriceBucket> buckets(BigDecimal min, BigDecimal max, Map<Integer, Long> countsByBucket) {
        long total = countsByBucket.values().stream().mapToLong(Long::longValue).sum();
        if (max.compareTo(min) == 0) {
            return List.of(new PriceBucket(min, max, total));
        }
        BigDecimal width = max.subtract(min).divide(BigDecimal.valueOf(BUCKETS), 6, RoundingMode.HALF_UP);
        List<PriceBucket> buckets = new ArrayList<>(BUCKETS);
        for (int i = 1; i <= BUCKETS; i++) {
            BigDecimal from = i == 1 ? min : boundary(min, width, i - 1);
            BigDecimal to = i == BUCKETS ? max : boundary(min, width, i);
            long count = countsByBucket.getOrDefault(i, 0L);
            if (i == BUCKETS) {
                count += countsByBucket.getOrDefault(BUCKETS + 1, 0L);
            }
            buckets.add(new PriceBucket(from, to, count));
        }
        return buckets;
    }

    private static BigDecimal boundary(BigDecimal min, BigDecimal width, int steps) {
        return min.add(width.multiply(BigDecimal.valueOf(steps))).setScale(2, RoundingMode.HALF_UP);
    }
}
