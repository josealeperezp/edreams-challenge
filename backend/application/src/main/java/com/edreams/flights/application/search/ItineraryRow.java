package com.edreams.flights.application.search;

import java.time.Instant;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.domain.model.Itinerary;

/** A stored itinerary with its row id (the keyset tiebreaker) and the run that last confirmed it. */
public record ItineraryRow(long id, Itinerary itinerary, IngestionRunId confirmedBy, Instant updatedAt) {
}
