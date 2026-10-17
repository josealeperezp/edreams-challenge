package com.edreams.mock.inventory;

import java.util.EnumMap;
import java.util.Map;

/** Holds the three provider views built from one spec and generator. */
public final class ProviderViews {

    private final Map<ProviderId, ProviderView> views = new EnumMap<>(ProviderId.class);

    public ProviderViews(InventorySpec spec, InventoryGenerator generator) {
        for (ProviderId provider : ProviderId.values()) {
            views.put(provider, new ProviderView(provider, spec, generator));
        }
    }

    public ProviderView get(ProviderId provider) {
        return views.get(provider);
    }
}
