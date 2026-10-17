package com.edreams.flights.application.port.out;

import java.util.Optional;

import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchStats;

/**
 * Page and stats cache keyed by inventory version. A completed run bumps the version, so stale
 * entries are never served and expire on their own. Implementations must never throw: a cache
 * outage degrades to a miss.
 */
public interface SearchCache {

    long currentVersion();

    long bumpVersion();

    Optional<SearchPage> get(String key);

    void put(String key, SearchPage page);

    Optional<SearchStats> getStats(String key);

    void putStats(String key, SearchStats stats);
}
