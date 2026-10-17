package com.edreams.flights.infrastructure.web;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edreams.flights.application.port.in.SearchItineraries;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.infrastructure.json.PageJson;
import com.edreams.flights.infrastructure.json.StatsJson;

/** Keyset-paginated search over the aggregated inventory, plus the aggregates behind the dashboard charts. */
@RestController
@RequestMapping("/api/itineraries")
public class SearchController {

    private final SearchItineraries search;
    private final int maxLimit;

    public SearchController(SearchItineraries search, @Value("${flights.search.max-limit:100}") int maxLimit) {
        this.search = search;
        this.maxLimit = maxLimit;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageJson search(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        SearchQuery query = SearchQuery.of(origin, destination, maxPrice, carrier, cursor, limit, maxLimit);
        return PageJson.from(search.search(query));
    }

    /** Same filters as the page, no cursor: counts and distributions over every matching itinerary. */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public StatsJson stats(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String carrier) {
        SearchQuery query = SearchQuery.of(origin, destination, maxPrice, carrier, null, null, maxLimit);
        return StatsJson.from(search.stats(query));
    }
}
