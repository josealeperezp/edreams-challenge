package com.edreams.flights.application.port.out;

import java.util.Collection;
import java.util.List;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.Itinerary;

/** The search index: one row per itinerary signature holding the winning offer. */
public interface ItineraryRepository {

    void upsertAll(IngestionRunId run, List<Itinerary> itineraries);

    int purgeNotConfirmedBy(Collection<IngestionRunId> keep);

    /** Keyset page: filters and cursor from the query, {@code limit} rows ordered by price then id. */
    List<ItineraryRow> search(SearchQuery query, int limit);

    /** Aggregates over every row the query's filters match, ignoring its cursor and limit. */
    SearchStats stats(SearchQuery query);

    long approximateCount();
}
