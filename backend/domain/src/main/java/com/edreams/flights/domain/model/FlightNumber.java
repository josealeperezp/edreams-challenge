package com.edreams.flights.domain.model;

import java.util.regex.Pattern;

/** Numeric flight number, 1 to 9999. Providers may pad or space it; parsing normalises that away. */
public record FlightNumber(int value) {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,4}");

    public FlightNumber {
        if (value < 1 || value > 9999) {
            throw new IllegalArgumentException("Flight number out of range: " + value);
        }
    }

    public static FlightNumber parse(String raw) {
        String digits = raw == null ? "" : raw.replace(" ", "").trim();
        if (!DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("Invalid flight number: " + raw);
        }
        return new FlightNumber(Integer.parseInt(digits));
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
