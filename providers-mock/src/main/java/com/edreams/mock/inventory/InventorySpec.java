package com.edreams.mock.inventory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import com.edreams.mock.config.MockProperties;

/**
 * Resolved inventory configuration. The inventory is a grid of (day, slot, route, carrier variant)
 * for direct flights plus (day, slot, route) for one-stop itineraries. An index maps to one cell,
 * interleaving days and itinerary kinds so any prefix of the inventory spans the whole date range
 * and contains one connection per two direct flights.
 */
public record InventorySpec(
        long seed,
        long size,
        LocalDate baseDate,
        int days,
        double corruptionRate,
        Map<ProviderId, Double> shares) {

    public static final int SLOTS_PER_DAY = 8;
    public static final int CARRIER_VARIANTS = 2;

    public static int directPerDay() {
        return Routes.COUNT * SLOTS_PER_DAY * CARRIER_VARIANTS;
    }

    public static int connectionsPerDay() {
        return Routes.COUNT * SLOTS_PER_DAY;
    }

    public static int perDay() {
        return directPerDay() + connectionsPerDay();
    }

    public long capacity() {
        return (long) days * perDay();
    }

    public double share(ProviderId provider) {
        return shares.get(provider);
    }

    public static InventorySpec from(MockProperties properties) {
        var inventory = properties.inventory();
        if (inventory.size() < 1) {
            throw new IllegalArgumentException("mock.inventory.size must be positive");
        }
        LocalDate baseDate = inventory.baseDate() == null || inventory.baseDate().isBlank()
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.parse(inventory.baseDate().trim());
        int days = (int) Math.max(inventory.days(), Math.ceilDiv(inventory.size(), (long) perDay()));
        var shares = new EnumMap<ProviderId, Double>(ProviderId.class);
        for (ProviderId provider : ProviderId.values()) {
            shares.put(provider, inventory.shares().of(provider));
        }
        return new InventorySpec(
                properties.seed(), inventory.size(), baseDate, days, inventory.corruptionRate(), Map.copyOf(shares));
    }
}
