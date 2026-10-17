package com.edreams.flights.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void normalisesFiltersAndDefaultsTheLimit() {
        SearchQuery query = SearchQuery.of(" mad", "bcn ", new BigDecimal("120"), "ib", null, null, 100);

        assertThat(query.origin().value()).isEqualTo("MAD");
        assertThat(query.destination().value()).isEqualTo("BCN");
        assertThat(query.maxPrice().amount()).isEqualByComparingTo("120.00");
        assertThat(query.carrier().code()).isEqualTo("IB");
        assertThat(query.cursor()).isNull();
        assertThat(query.limit()).isEqualTo(SearchQuery.DEFAULT_LIMIT);
        assertThat(query.cacheKey()).isEqualTo("MAD|BCN|120.00|IB||20");
    }

    @Test
    void emptyFiltersMeanAny() {
        SearchQuery query = SearchQuery.of("", null, null, " ", "", 5, 100);
        assertThat(query.origin()).isNull();
        assertThat(query.destination()).isNull();
        assertThat(query.maxPrice()).isNull();
        assertThat(query.carrier()).isNull();
        assertThat(query.cacheKey()).isEqualTo("||||" + "|5");
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> SearchQuery.of("MA", null, null, null, null, null, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchQuery.of(null, null, BigDecimal.ZERO, null, null, null, 100)).hasMessageContaining("maxPrice");
        assertThatThrownBy(() -> SearchQuery.of(null, null, null, null, null, 0, 100)).hasMessageContaining("limit");
        assertThatThrownBy(() -> SearchQuery.of(null, null, null, null, null, 101, 100)).hasMessageContaining("limit");
        assertThatThrownBy(() -> SearchQuery.of(null, null, null, null, "garbage", null, 100)).hasMessage("Invalid cursor");
    }

    @Test
    void filtersKeyIgnoresCursorAndLimit() {
        SearchQuery paged = SearchQuery.of("MAD", "BCN", new BigDecimal("120"), "IB", null, 50, 100);
        SearchQuery withCursor = new SearchQuery(paged.origin(), paged.destination(), paged.maxPrice(), paged.carrier(),
                new Cursor(new BigDecimal("99.00"), 42), 7);

        assertThat(paged.filtersKey()).isEqualTo("MAD|BCN|120.00|IB");
        assertThat(withCursor.filtersKey()).isEqualTo(paged.filtersKey());
        assertThat(withCursor.cacheKey()).isNotEqualTo(paged.cacheKey());
    }
}
