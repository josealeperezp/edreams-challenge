package com.edreams.mock.inventory;

import java.time.OffsetDateTime;

/** One flight leg. Times carry the local offset of the airport they refer to. */
public record Segment(
        String carrier,
        int flightNumber,
        String origin,
        String destination,
        OffsetDateTime departure,
        OffsetDateTime arrival) {

    public Segment withCarrier(String newCarrier) {
        return new Segment(newCarrier, flightNumber, origin, destination, departure, arrival);
    }

    public Segment withOrigin(String newOrigin) {
        return new Segment(carrier, flightNumber, newOrigin, destination, departure, arrival);
    }

    public Segment withDestination(String newDestination) {
        return new Segment(carrier, flightNumber, origin, newDestination, departure, arrival);
    }

    public Segment withDeparture(OffsetDateTime newDeparture) {
        return new Segment(carrier, flightNumber, origin, destination, newDeparture, arrival);
    }

    public Segment withArrival(OffsetDateTime newArrival) {
        return new Segment(carrier, flightNumber, origin, destination, departure, newArrival);
    }
}
