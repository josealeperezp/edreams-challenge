package com.edreams.mock.chaos;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.edreams.mock.config.MockProperties;
import com.edreams.mock.inventory.ProviderId;

/** Current chaos settings per provider. Defaults come from configuration; the admin endpoint changes them at runtime. */
@Component
public class ChaosRegistry {

    private final Map<ProviderId, ChaosSettings> defaults = new EnumMap<>(ProviderId.class);
    private final Map<ProviderId, AtomicReference<ChaosSettings>> current = new EnumMap<>(ProviderId.class);

    public ChaosRegistry(MockProperties properties) {
        MockProperties.Chaos chaos = properties.chaos();
        for (ProviderId provider : ProviderId.values()) {
            MockProperties.ProviderOverride override = chaos.providers().get(provider.path());
            ChaosSettings settings = new ChaosSettings(
                    override != null && override.enabled() != null ? override.enabled() : chaos.enabled(),
                    override != null && override.latencyMinMs() != null ? override.latencyMinMs() : chaos.latencyMinMs(),
                    override != null && override.latencyMaxMs() != null ? override.latencyMaxMs() : chaos.latencyMaxMs(),
                    override != null && override.errorRate() != null ? override.errorRate() : chaos.errorRate());
            defaults.put(provider, settings);
            current.put(provider, new AtomicReference<>(settings));
        }
    }

    public ChaosSettings get(ProviderId provider) {
        return current.get(provider).get();
    }

    public ChaosSettings update(ProviderId provider, ChaosSettings settings) {
        current.get(provider).set(settings);
        return settings;
    }

    public ChaosSettings reset(ProviderId provider) {
        return update(provider, defaults.get(provider));
    }

    /** Settings of every provider keyed by path name, in provider order. */
    public Map<String, ChaosSettings> all() {
        var all = new LinkedHashMap<String, ChaosSettings>();
        for (ProviderId provider : ProviderId.values()) {
            all.put(provider.path(), get(provider));
        }
        return all;
    }
}
