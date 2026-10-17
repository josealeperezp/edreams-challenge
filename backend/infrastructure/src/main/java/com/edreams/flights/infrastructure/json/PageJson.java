package com.edreams.flights.infrastructure.json;

import java.util.List;

import com.edreams.flights.application.search.SearchPage;

/** API and cache representation of a search page. */
public record PageJson(List<ItineraryJson> items, String nextCursor, boolean hasNext, InventoryJson inventory) {

    public record InventoryJson(long version, long approximateItineraries, RunJson latestRun) {
    }

    public static PageJson from(SearchPage page) {
        return new PageJson(page.items().stream().map(ItineraryJson::from).toList(), page.nextCursor(), page.hasNext(),
                new InventoryJson(page.inventory().version(), page.inventory().approximateItineraries(),
                        page.inventory().latestRun() == null ? null : RunJson.from(page.inventory().latestRun())));
    }

    public SearchPage toPage() {
        return new SearchPage(items.stream().map(ItineraryJson::toRow).toList(), nextCursor, hasNext,
                new SearchPage.Inventory(inventory.version(), inventory.approximateItineraries(),
                        inventory.latestRun() == null ? null : inventory.latestRun().toRun()));
    }
}
