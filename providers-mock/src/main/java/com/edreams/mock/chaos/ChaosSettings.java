package com.edreams.mock.chaos;

/** Latency and failure injection for one provider. Immutable and validated. */
public record ChaosSettings(boolean enabled, int latencyMinMs, int latencyMaxMs, double errorRate) {

    public static final int MAX_LATENCY_MS = 60_000;

    public ChaosSettings {
        if (latencyMinMs < 0 || latencyMaxMs < latencyMinMs || latencyMaxMs > MAX_LATENCY_MS) {
            throw new IllegalArgumentException(
                    "latency must satisfy 0 <= min <= max <= " + MAX_LATENCY_MS + ", got " + latencyMinMs + ".." + latencyMaxMs);
        }
        if (errorRate < 0 || errorRate > 1) {
            throw new IllegalArgumentException("errorRate must be within [0, 1], got " + errorRate);
        }
    }
}
