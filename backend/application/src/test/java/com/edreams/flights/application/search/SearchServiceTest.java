package com.edreams.flights.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.ProviderId;

class SearchServiceTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final SearchCache cache = mock(SearchCache.class);
    private final IngestionRunRepository runs = mock(IngestionRunRepository.class);
    private final SearchService service = new SearchService(repository, cache, runs);

    @Test
    void fetchesOneExtraRowToKnowWhetherANextPageExists() {
        SearchQuery query = SearchQuery.of("MAD", "BCN", null, null, null, 2, 100);
        when(cache.currentVersion()).thenReturn(7L);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(repository.search(eq(query), eq(3))).thenReturn(List.of(TestRows.row(1, "80"), TestRows.row(2, "85"), TestRows.row(3, "90")));
        when(repository.approximateCount()).thenReturn(1234L);
        when(runs.latest()).thenReturn(Optional.empty());

        SearchPage page = service.search(query);

        assertThat(page.items()).extracting(ItineraryRow::id).containsExactly(1L, 2L);
        assertThat(page.hasNext()).isTrue();
        assertThat(Cursor.decode(page.nextCursor())).isEqualTo(Cursor.after(page.items().getLast()));
        assertThat(page.inventory().version()).isEqualTo(7L);
        assertThat(page.inventory().approximateItineraries()).isEqualTo(1234L);
        verify(cache).put(eq("7:" + query.cacheKey()), any(SearchPage.class));
    }

    @Test
    void lastPageHasNoCursor() {
        SearchQuery query = SearchQuery.of(null, null, null, null, null, 5, 100);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(repository.search(eq(query), eq(6))).thenReturn(List.of(TestRows.row(1, "80")));
        when(runs.latest()).thenReturn(Optional.empty());

        SearchPage page = service.search(query);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void servesFromCacheWhenTheVersionMatches() {
        SearchQuery query = SearchQuery.of("MAD", null, null, null, null, null, 100);
        SearchPage cached = new SearchPage(List.of(TestRows.row(9, "70")), null, false, new SearchPage.Inventory(3, 1, null));
        when(cache.currentVersion()).thenReturn(3L);
        when(cache.get("3:" + query.cacheKey())).thenReturn(Optional.of(cached));

        assertThat(service.search(query)).isSameAs(cached);
        verify(repository, never()).search(any(), anyInt());
    }

    @Test
    void statsAreComputedOnceAndCachedUnderTheVersionedFilters() {
        SearchQuery query = SearchQuery.of("MAD", "BCN", null, null, null, null, 100);
        SearchStats computed = new SearchStats(2, new BigDecimal("80.00"), new BigDecimal("90.00"),
                List.of(new SearchStats.PriceBucket(new BigDecimal("80.00"), new BigDecimal("90.00"), 2)),
                List.of(new SearchStats.CarrierCount(new Carrier("IB"), 2, new BigDecimal("80.00"))),
                List.of(new SearchStats.ProviderCount(ProviderId.BETA, 2)), List.of(new SearchStats.StopsCount(0, 2)));
        when(cache.currentVersion()).thenReturn(7L);
        when(cache.getStats(anyString())).thenReturn(Optional.empty());
        when(repository.stats(query)).thenReturn(computed);

        assertThat(service.stats(query)).isSameAs(computed);

        verify(cache).putStats("7:stats:MAD|BCN||", computed);
    }

    @Test
    void cachedStatsSkipTheRepository() {
        SearchQuery query = SearchQuery.of(null, null, new BigDecimal("120"), "IB", new Cursor(new BigDecimal("99.00"), 42).encode(), 5, 100);
        SearchStats cached = SearchStats.empty();
        when(cache.currentVersion()).thenReturn(3L);
        when(cache.getStats("3:stats:||120.00|IB")).thenReturn(Optional.of(cached));

        assertThat(service.stats(query)).isSameAs(cached);
        verify(repository, never()).stats(any());
    }
}
