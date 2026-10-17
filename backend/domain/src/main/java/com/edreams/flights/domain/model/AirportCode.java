package com.edreams.flights.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IATA three-letter airport code, for example {@code MAD}. Always upper case.
 */
public record AirportCode(String value) {

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");

    public AirportCode {
        if (value == null || !IATA.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid IATA airport code: " + value);
        }
    }

    /** Lenient factory for provider input: trims and upper-cases before validating. */
    public static AirportCode of(String raw) {
        return new AirportCode(raw == null ? null : raw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
