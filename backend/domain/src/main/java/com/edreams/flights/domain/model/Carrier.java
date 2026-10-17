package com.edreams.flights.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** IATA two-character marketing carrier code, for example {@code IB} or {@code U2}. */
public record Carrier(String code) {

    private static final Pattern IATA = Pattern.compile("[A-Z0-9]{2}");

    public Carrier {
        if (code == null || !IATA.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid carrier code: " + code);
        }
    }

    public static Carrier of(String raw) {
        return new Carrier(raw == null ? null : raw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return code;
    }
}
