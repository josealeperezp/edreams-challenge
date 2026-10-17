package com.edreams.flights.infrastructure.json;

import java.time.OffsetDateTime;

import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Segment;

/** Flat segment shape shared by the JSONB columns, the Redis cache and the API. */
public record SegmentJson(
        String carrier,
        int flightNumber,
        String origin,
        String destination,
        OffsetDateTime departure,
        OffsetDateTime arrival) {

    public static SegmentJson from(Segment s) {
        return new SegmentJson(s.carrier().code(), s.flightNumber().value(), s.origin().value(), s.destination().value(),
                s.departure(), s.arrival());
    }

    public Segment toSegment() {
        return new Segment(new Carrier(carrier), new FlightNumber(flightNumber), new AirportCode(origin),
                new AirportCode(destination), departure, arrival);
    }
}
