package com.edreams.mock.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.edreams.mock.inventory.ProviderId;

/** All knobs of the mock, bound from {@code mock.*}. The compose file maps them to {@code MOCK_*} env vars. */
@ConfigurationProperties(prefix = "mock")
public record MockProperties(
        @DefaultValue("42") long seed,
        @DefaultValue Inventory inventory,
        @DefaultValue Chaos chaos) {

    public record Inventory(
            @DefaultValue("100000") long size,
            @DefaultValue("30") int days,
            String baseDate,
            @DefaultValue("0.015") double corruptionRate,
            @DefaultValue Shares shares) {
    }

    public record Shares(
            @DefaultValue("0.70") double alpha,
            @DefaultValue("0.65") double beta,
            @DefaultValue("0.60") double gamma) {

        public double of(ProviderId provider) {
            return switch (provider) {
                case ALPHA -> alpha;
                case BETA -> beta;
                case GAMMA -> gamma;
            };
        }
    }

    public record Chaos(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int latencyMinMs,
            @DefaultValue("5000") int latencyMaxMs,
            @DefaultValue("0.10") double errorRate,
            Map<String, ProviderOverride> providers) {

        public Chaos {
            providers = providers == null ? Map.of() : Map.copyOf(providers);
        }
    }

    /** Per-provider override of the global chaos defaults; null fields fall back to the global value. */
    public record ProviderOverride(Boolean enabled, Integer latencyMinMs, Integer latencyMaxMs, Double errorRate) {
    }
}
