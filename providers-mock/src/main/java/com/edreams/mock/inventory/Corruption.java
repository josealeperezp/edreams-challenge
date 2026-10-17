package com.edreams.mock.inventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Ways a provider's copy of an itinerary can be broken. The master itinerary is always valid;
 * corruption is applied per provider so the same itinerary can be valid from one and broken from another.
 */
public enum Corruption {
    /** A leg whose destination equals its origin. */
    DEAD_LEG,
    /** Second leg departs from a different airport than the first leg arrives at. */
    BAD_CONNECTION,
    /** Arrival before departure. */
    NEGATIVE_DURATION,
    /** Ten-minute layover, below any sane minimum connection time. */
    SHORT_LAYOVER,
    /** Empty carrier code. */
    MISSING_CARRIER;

    public static Corruption pick(long hash, int segmentCount) {
        Corruption[] all = values();
        Corruption chosen = all[Hashing.bounded(hash, all.length)];
        if (segmentCount < 2 && chosen.needsConnection()) {
            return (hash & 1) == 0 ? DEAD_LEG : NEGATIVE_DURATION;
        }
        return chosen;
    }

    boolean needsConnection() {
        return this == BAD_CONNECTION || this == SHORT_LAYOVER;
    }

    public List<Segment> apply(List<Segment> segments) {
        var legs = new ArrayList<>(segments);
        Segment first = legs.getFirst();
        switch (this) {
            case DEAD_LEG -> legs.set(0, first.withDestination(first.origin()));
            case NEGATIVE_DURATION -> legs.set(0, first.withArrival(first.departure().minusMinutes(45)));
            case MISSING_CARRIER -> legs.set(0, first.withCarrier(""));
            case BAD_CONNECTION -> {
                Segment second = legs.get(1);
                legs.set(1, second.withOrigin(otherAirport(second.origin(), second.destination())));
            }
            case SHORT_LAYOVER -> {
                Segment second = legs.get(1);
                Duration flightTime = Duration.between(second.departure(), second.arrival());
                var departure = first.arrival().plusMinutes(10);
                legs.set(1, second.withDeparture(departure).withArrival(departure.plus(flightTime)));
            }
        }
        return List.copyOf(legs);
    }

    private static String otherAirport(String a, String b) {
        for (Airport airport : Airports.ALL) {
            if (!airport.code().equals(a) && !airport.code().equals(b)) {
                return airport.code();
            }
        }
        throw new IllegalStateException("catalog too small");
    }
}
