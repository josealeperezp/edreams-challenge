package com.edreams.flights.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The aggregated, sellable itinerary: the cheapest provider's offer with the OTA markup applied and
 * every provider's quote kept for provenance. This is what the search index stores and returns.
 */
public record Itinerary(
        ItinerarySignature signature,
        List<Segment> segments,
        Money basePrice,
        Money price,
        ProviderId bestProvider,
        List<ProviderQuote> quotes) {

    public Itinerary {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(bestProvider, "bestProvider");
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("An itinerary needs at least one segment");
        }
        segments = List.copyOf(segments);
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
    }

    public AirportCode origin() {
        return segments.getFirst().origin();
    }

    public AirportCode destination() {
        return segments.getLast().destination();
    }

    public OffsetDateTime departureAt() {
        return segments.getFirst().departure();
    }

    public OffsetDateTime arrivalAt() {
        return segments.getLast().arrival();
    }

    /** Marketing carrier of the first segment; the one the carrier filter matches on. */
    public Carrier carrier() {
        return segments.getFirst().carrier();
    }

    public int stops() {
        return segments.size() - 1;
    }
}
