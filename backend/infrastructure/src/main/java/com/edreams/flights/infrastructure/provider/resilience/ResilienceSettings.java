package com.edreams.flights.infrastructure.provider.resilience;

import java.time.Duration;

/** Retry and circuit-breaker tunables shared by the three providers. */
public record ResilienceSettings(
        int retryAttempts,
        Duration retryInitialWait,
        int breakerWindow,
        float breakerFailureRatePercent,
        Duration breakerOpenDuration) {

    public static ResilienceSettings defaults() {
        return new ResilienceSettings(3, Duration.ofMillis(500), 4, 50f, Duration.ofSeconds(30));
    }
}
