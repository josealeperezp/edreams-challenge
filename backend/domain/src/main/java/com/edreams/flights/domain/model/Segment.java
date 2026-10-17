package com.edreams.flights.domain.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/** One validated flight leg. Times keep the offset the provider gave, so local times can be shown. */
public record Segment(
        Carrier carrier,
        FlightNumber flightNumber,
        AirportCode origin,
        AirportCode destination,
        OffsetDateTime departure,
        OffsetDateTime arrival) {

    public Segment {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(flightNumber, "flightNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
    }

    public Duration duration() {
        return Duration.between(departure, arrival);
    }

    /** Carrier code and number, for example {@code IB845}. */
    public String flightCode() {
        return carrier.code() + flightNumber.value();
    }
}
