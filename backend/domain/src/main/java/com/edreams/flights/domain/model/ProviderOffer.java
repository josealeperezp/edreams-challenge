package com.edreams.flights.domain.model;

import java.util.List;
import java.util.Objects;

/** A validated, normalised offer from one provider. Its signature identifies the itinerary across providers. */
public record ProviderOffer(ProviderId provider, String providerOfferId, List<Segment> segments, Money price) {

    public ProviderOffer {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(price, "price");
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("An offer needs at least one segment");
        }
        segments = List.copyOf(segments);
    }

    public ItinerarySignature signature() {
        return ItinerarySignature.of(segments);
    }
}
