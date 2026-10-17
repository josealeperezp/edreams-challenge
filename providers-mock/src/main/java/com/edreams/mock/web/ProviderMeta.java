package com.edreams.mock.web;

import java.time.LocalDate;

import com.edreams.mock.chaos.ChaosSettings;
import com.edreams.mock.inventory.InventorySpec;
import com.edreams.mock.inventory.ProviderId;
import com.edreams.mock.inventory.ProviderView;

/** What a provider will stream and how it currently misbehaves. Never subject to chaos. */
public record ProviderMeta(
        String provider,
        long seed,
        LocalDate baseDate,
        int days,
        long inventorySize,
        double share,
        long expectedOffers,
        long expectedRows,
        double corruptionRate,
        ChaosSettings chaos) {

    public static ProviderMeta of(ProviderView view, ChaosSettings chaos) {
        InventorySpec spec = view.spec();
        ProviderView.Counts counts = view.counts();
        long rows = view.provider() == ProviderId.GAMMA ? counts.segments() : counts.offers();
        return new ProviderMeta(
                view.provider().name(), spec.seed(), spec.baseDate(), spec.days(), spec.size(), view.share(),
                counts.offers(), rows, spec.corruptionRate(), chaos);
    }
}
