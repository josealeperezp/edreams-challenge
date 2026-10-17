package com.edreams.flights.infrastructure.json;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;

/** API and cache representation of one stored itinerary. */
public record ItineraryJson(
        long id,
        String signature,
        String origin,
        String destination,
        String carrier,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt,
        int stops,
        BigDecimal basePrice,
        BigDecimal price,
        String currency,
        String bestProvider,
        List<SegmentJson> segments,
        List<QuoteJson> quotes,
        UUID confirmedByRun,
        Instant updatedAt) {

    public static ItineraryJson from(ItineraryRow row) {
        Itinerary it = row.itinerary();
        return new ItineraryJson(row.id(), it.signature().value(), it.origin().value(), it.destination().value(),
                it.carrier().code(), it.departureAt(), it.arrivalAt(), it.stops(), it.basePrice().amount(), it.price().amount(),
                it.price().currency().getCurrencyCode(), it.bestProvider().name(),
                it.segments().stream().map(SegmentJson::from).toList(),
                it.quotes().stream().map(QuoteJson::from).toList(),
                row.confirmedBy() == null ? null : row.confirmedBy().value(), row.updatedAt());
    }

    public ItineraryRow toRow() {
        Itinerary itinerary = new Itinerary(new ItinerarySignature(signature),
                segments.stream().map(SegmentJson::toSegment).toList(),
                Money.eur(basePrice), Money.eur(price), ProviderId.valueOf(bestProvider),
                quotes.stream().map(QuoteJson::toQuote).toList());
        return new ItineraryRow(id, itinerary, confirmedByRun == null ? null : new IngestionRunId(confirmedByRun), updatedAt);
    }
}
