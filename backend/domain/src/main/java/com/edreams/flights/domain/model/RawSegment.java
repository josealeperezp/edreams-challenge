package com.edreams.flights.domain.model;

import java.time.OffsetDateTime;

/**
 * A leg exactly as a provider adapter decoded it, before any domain validation. Codes are raw
 * strings so the validator, not the adapter, decides what is missing or malformed.
 */
public record RawSegment(
        String carrier,
        String flightNumber,
        String origin,
        String destination,
        OffsetDateTime departure,
        OffsetDateTime arrival) {
}
