package com.edreams.flights.application.search;

import java.util.List;

import com.edreams.flights.application.ingestion.IngestionRun;

/** One keyset page plus what the client needs to judge freshness. No total count: see SOLUTION.md. */
public record SearchPage(List<ItineraryRow> items, String nextCursor, boolean hasNext, Inventory inventory) {

    public SearchPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Inventory version (cache generation), approximate size and the latest run. */
    public record Inventory(long version, long approximateItineraries, IngestionRun latestRun) {
    }
}
