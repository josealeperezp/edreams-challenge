package com.edreams.mock.inventory;

import java.util.List;

/** Static carrier catalog. */
public final class Carriers {

    public static final List<Carrier> ALL = List.of(
            new Carrier("IB", "Iberia"),
            new Carrier("VY", "Vueling"),
            new Carrier("BA", "British Airways"),
            new Carrier("AF", "Air France"),
            new Carrier("LH", "Lufthansa"),
            new Carrier("KL", "KLM"),
            new Carrier("AZ", "ITA Airways"),
            new Carrier("TP", "TAP Air Portugal"),
            new Carrier("FR", "Ryanair"),
            new Carrier("U2", "easyJet"));

    private Carriers() {
    }

    public static Carrier byCode(String code) {
        return ALL.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown carrier: " + code));
    }
}
