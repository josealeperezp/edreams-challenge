package com.edreams.mock.inventory;

import java.math.BigDecimal;
import java.util.List;

/** The canonical, always valid itinerary at an inventory index. Providers derive their own view from it. */
public record MasterItinerary(long index, List<Segment> segments, BigDecimal basePrice, String currency) {

    public Segment first() {
        return segments.getFirst();
    }

    public Segment last() {
        return segments.getLast();
    }
}
