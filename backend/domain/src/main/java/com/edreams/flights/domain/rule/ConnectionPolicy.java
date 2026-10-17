package com.edreams.flights.domain.rule;

import java.time.Duration;
import java.util.Objects;

/** Bounds for the time between two consecutive segments. */
public record ConnectionPolicy(Duration minConnection, Duration maxLayover) {

    public ConnectionPolicy {
        Objects.requireNonNull(minConnection, "minConnection");
        Objects.requireNonNull(maxLayover, "maxLayover");
        if (minConnection.isNegative() || maxLayover.compareTo(minConnection) < 0) {
            throw new IllegalArgumentException("Need 0 <= minConnection <= maxLayover, got " + minConnection + " and " + maxLayover);
        }
    }
}
