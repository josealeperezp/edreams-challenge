package com.edreams.flights.application.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.edreams.flights.application.search.SearchStats.PriceBucket;

class SearchStatsTest {

    @Test
    void splitsTheRangeIntoTwentyBucketsAndFoldsTheMaximumIntoTheLast() {
        List<PriceBucket> buckets = SearchStats.buckets(new BigDecimal("50.00"), new BigDecimal("70.00"), Map.of(1, 3L, 20, 1L, 21, 2L));

        assertThat(buckets).hasSize(SearchStats.BUCKETS);
        assertThat(buckets.getFirst()).isEqualTo(new PriceBucket(new BigDecimal("50.00"), new BigDecimal("51.00"), 3));
        assertThat(buckets.get(1)).isEqualTo(new PriceBucket(new BigDecimal("51.00"), new BigDecimal("52.00"), 0));
        assertThat(buckets.getLast()).isEqualTo(new PriceBucket(new BigDecimal("69.00"), new BigDecimal("70.00"), 3));
        assertThat(buckets.stream().mapToLong(PriceBucket::count).sum()).isEqualTo(6);
    }

    @Test
    void aSinglePriceIsOneBucket() {
        assertThat(SearchStats.buckets(new BigDecimal("89.78"), new BigDecimal("89.78"), Map.of(1, 5L)))
                .containsExactly(new PriceBucket(new BigDecimal("89.78"), new BigDecimal("89.78"), 5));
    }

    @Test
    void emptyStatsHaveNoBoundsAndNoGroups() {
        SearchStats empty = SearchStats.empty();
        assertThat(empty.total()).isZero();
        assertThat(empty.minPrice()).isNull();
        assertThat(empty.priceBuckets()).isEmpty();
        assertThat(empty.byCarrier()).isEmpty();
        assertThat(new SearchStats(0, null, null, null, null, null, null).byProvider()).isEmpty();
    }
}
