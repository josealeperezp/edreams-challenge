package com.edreams.mock.inventory;

/**
 * Directed airport pairs, indexed densely in {@code [0, COUNT)} so a route index can be part of
 * the mixed-radix decomposition of an inventory index.
 */
public final class Routes {

    private static final int AIRPORTS = Airports.ALL.size();

    public static final int COUNT = AIRPORTS * (AIRPORTS - 1);

    private Routes() {
    }

    public static Airport origin(int route) {
        return Airports.ALL.get(route / (AIRPORTS - 1));
    }

    public static Airport destination(int route) {
        int originIndex = route / (AIRPORTS - 1);
        int k = route % (AIRPORTS - 1);
        return Airports.ALL.get(k >= originIndex ? k + 1 : k);
    }

    public static int index(Airport origin, Airport destination) {
        int o = Airports.ALL.indexOf(origin);
        int d = Airports.ALL.indexOf(destination);
        if (o < 0 || d < 0 || o == d) {
            throw new IllegalArgumentException("Invalid route " + origin.code() + "-" + destination.code());
        }
        return o * (AIRPORTS - 1) + (d > o ? d - 1 : d);
    }
}
