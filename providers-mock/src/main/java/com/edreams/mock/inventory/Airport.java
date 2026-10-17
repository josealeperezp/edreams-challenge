package com.edreams.mock.inventory;

import java.time.ZoneId;

/** An airport of the catalog. Coordinates drive flight durations and base prices. */
public record Airport(String code, String name, ZoneId zone, double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Great-circle distance in kilometres (haversine). */
    public double distanceKmTo(Airport other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
