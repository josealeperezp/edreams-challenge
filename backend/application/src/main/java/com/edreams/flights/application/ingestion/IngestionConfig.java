package com.edreams.flights.application.ingestion;

import java.time.Duration;

/** Tunables of a run. */
public record IngestionConfig(int batchSize, Duration providerBudget, int keepRuns) {

    public IngestionConfig {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (providerBudget == null || providerBudget.isNegative() || providerBudget.isZero()) {
            throw new IllegalArgumentException("providerBudget must be positive");
        }
        if (keepRuns < 1) {
            throw new IllegalArgumentException("keepRuns must be positive");
        }
    }
}
