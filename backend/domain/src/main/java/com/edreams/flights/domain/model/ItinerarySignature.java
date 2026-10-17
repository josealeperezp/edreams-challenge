package com.edreams.flights.domain.model;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The deduplication key. Two offers are the same itinerary when they share airline, flight numbers,
 * connections and departure times: each segment contributes carrier and number, origin, destination
 * and the departure instant in UTC truncated to the minute. Price, arrival time, provider ids and
 * formatting quirks are excluded by construction, for example {@code IB845|MAD|BCN|2026-11-02T06:15:00Z}.
 */
public record ItinerarySignature(String value) {

    private static final String SEGMENT_SEPARATOR = ";";
    private static final String PART_SEPARATOR = "|";

    public ItinerarySignature {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Signature cannot be blank");
        }
    }

    public static ItinerarySignature of(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Signature needs at least one segment");
        }
        return new ItinerarySignature(segments.stream()
                .map(ItinerarySignature::segmentKey)
                .collect(Collectors.joining(SEGMENT_SEPARATOR)));
    }

    private static String segmentKey(Segment s) {
        String departureUtc = DateTimeFormatter.ISO_INSTANT.format(s.departure().toInstant().truncatedTo(ChronoUnit.MINUTES));
        return s.flightCode() + PART_SEPARATOR + s.origin() + PART_SEPARATOR + s.destination() + PART_SEPARATOR + departureUtc;
    }

    @Override
    public String toString() {
        return value;
    }
}
