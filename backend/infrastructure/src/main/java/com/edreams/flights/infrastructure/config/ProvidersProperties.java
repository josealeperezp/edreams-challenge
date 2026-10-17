package com.edreams.flights.infrastructure.config;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.edreams.flights.domain.model.ProviderId;

/** Connection settings for the three GDS providers, bound from the {@code providers.*} properties. */
@ConfigurationProperties(prefix = "providers")
public record ProvidersProperties(ProviderSettings alpha, ProviderSettings beta, ProviderSettings gamma) {

    public ProvidersProperties {
        Objects.requireNonNull(alpha, "providers.alpha is required");
        Objects.requireNonNull(beta, "providers.beta is required");
        Objects.requireNonNull(gamma, "providers.gamma is required");
    }

    public record ProviderSettings(String baseUrl, Duration timeout) {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

        public ProviderSettings {
            Objects.requireNonNull(baseUrl, "base-url is required");
            timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        }
    }

    /** Settings keyed by provider, in {@link ProviderId} order. */
    public Map<ProviderId, ProviderSettings> byId() {
        var byId = new EnumMap<ProviderId, ProviderSettings>(ProviderId.class);
        byId.put(ProviderId.ALPHA, alpha);
        byId.put(ProviderId.BETA, beta);
        byId.put(ProviderId.GAMMA, gamma);
        return Collections.unmodifiableMap(byId);
    }
}
