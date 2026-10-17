package com.edreams.flights.application.search;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.edreams.flights.application.port.in.SearchItineraries;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.SearchCache;

/** Serves pages and stats from the cache when the inventory version matches, otherwise from one query each. */
public final class SearchService implements SearchItineraries {

    private final ItineraryRepository repository;
    private final SearchCache cache;
    private final IngestionRunRepository runs;
    private volatile long countedVersion = -1;
    private volatile long countedTotal;

    public SearchService(ItineraryRepository repository, SearchCache cache, IngestionRunRepository runs) {
        this.repository = Objects.requireNonNull(repository);
        this.cache = Objects.requireNonNull(cache);
        this.runs = Objects.requireNonNull(runs);
    }

    @Override
    public SearchPage search(SearchQuery query) {
        long version = cache.currentVersion();
        String key = version + ":" + query.cacheKey();
        Optional<SearchPage> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<ItineraryRow> rows = repository.search(query, query.limit() + 1);
        boolean hasNext = rows.size() > query.limit();
        List<ItineraryRow> items = hasNext ? rows.subList(0, query.limit()) : rows;
        String nextCursor = hasNext ? Cursor.after(items.getLast()).encode() : null;

        SearchPage page = new SearchPage(items, nextCursor, hasNext,
                new SearchPage.Inventory(version, total(version), runs.latest().orElse(null)));
        cache.put(key, page);
        return page;
    }

    @Override
    public SearchStats stats(SearchQuery query) {
        long version = cache.currentVersion();
        String key = version + ":stats:" + query.filtersKey();
        Optional<SearchStats> cached = cache.getStats(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        SearchStats stats = repository.stats(query);
        cache.putStats(key, stats);
        return stats;
    }

    /** The inventory only changes when the version does, so count once per version. */
    private long total(long version) {
        if (countedVersion != version) {
            countedTotal = repository.approximateCount();
            countedVersion = version;
        }
        return countedTotal;
    }
}
