package com.edreams.flights.bootstrap;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Business and operational tunables, bound from {@code flights.*}. */
@ConfigurationProperties(prefix = "flights")
public record FlightsProperties(
        @DefaultValue Pricing pricing,
        @DefaultValue Validation validation,
        @DefaultValue Ingestion ingestion,
        @DefaultValue Search search,
        @DefaultValue Resilience resilience) {

    public record Pricing(@DefaultValue("5") BigDecimal markupPercent) {
    }

    public record Validation(@DefaultValue("30m") Duration minConnection, @DefaultValue("24h") Duration maxLayover) {
    }

    public record Ingestion(
            @DefaultValue("true") boolean runOnStartup,
            @DefaultValue("0 0 3 * * *") String cron,
            @DefaultValue("1000") int batchSize,
            @DefaultValue("120s") Duration providerBudget,
            @DefaultValue("3") int keepRuns) {
    }

    public record Search(@DefaultValue("100") int maxLimit, @DefaultValue("60s") Duration cacheTtl) {
    }

    public record Resilience(
            @DefaultValue("3") int retryAttempts,
            @DefaultValue("500ms") Duration retryInitialWait,
            @DefaultValue("4") int breakerWindow,
            @DefaultValue("50") float breakerFailureRatePercent,
            @DefaultValue("30s") Duration breakerOpenDuration) {
    }
}
