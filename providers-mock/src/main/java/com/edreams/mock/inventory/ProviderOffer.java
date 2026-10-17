package com.edreams.mock.inventory;

import java.math.BigDecimal;
import java.util.List;

/** One provider's offer for an itinerary: its own id and price, possibly corrupted legs. */
public record ProviderOffer(
        String offerId,
        ProviderId provider,
        List<Segment> legs,
        BigDecimal price,
        String currency,
        Corruption corruption) {

    public boolean corrupted() {
        return corruption != null;
    }
}
