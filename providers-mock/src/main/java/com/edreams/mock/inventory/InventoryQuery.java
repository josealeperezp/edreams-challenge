package com.edreams.mock.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Optional filters on a provider stream. A null field means "no filter". */
public record InventoryQuery(String origin, String destination, LocalDate date) {

    public static final InventoryQuery ALL = new InventoryQuery(null, null, null);

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");

    public static InventoryQuery of(String origin, String destination, String date) {
        return new InventoryQuery(iata(origin, "origin"), iata(destination, "destination"), date(date));
    }

    public boolean matches(ProviderOffer offer) {
        Segment first = offer.legs().getFirst();
        Segment last = offer.legs().getLast();
        return (origin == null || origin.equals(first.origin()))
                && (destination == null || destination.equals(last.destination()))
                && (date == null || date.equals(first.departure().toLocalDate()));
    }

    private static String iata(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!IATA.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid " + name + " airport code: " + raw);
        }
        return code;
    }

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date, expected yyyy-MM-dd: " + raw);
        }
    }
}
