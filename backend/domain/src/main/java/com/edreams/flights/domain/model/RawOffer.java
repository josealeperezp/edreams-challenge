package com.edreams.flights.domain.model;

import java.math.BigDecimal;
import java.util.List;

/** A provider's offer as decoded by its adapter, not yet validated. */
public record RawOffer(
        ProviderId provider,
        String providerOfferId,
        List<RawSegment> segments,
        BigDecimal price,
        String currency) {
}
