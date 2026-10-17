package com.edreams.flights.application.port.in;

import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.application.search.SearchStats;

public interface SearchItineraries {

    SearchPage search(SearchQuery query);

    /** Aggregates over every row the query's filters match; cursor and limit are ignored. */
    SearchStats stats(SearchQuery query);
}
