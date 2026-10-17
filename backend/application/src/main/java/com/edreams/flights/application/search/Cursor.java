package com.edreams.flights.application.search;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Keyset position: the price and row id of the last item served. Opaque to clients, it lets the
 * next page start exactly after that row with one indexed range scan, regardless of page depth.
 */
public record Cursor(BigDecimal price, long id) {

    public Cursor {
        if (price == null) {
            throw new IllegalArgumentException("Cursor price is required");
        }
    }

    public static Cursor after(ItineraryRow row) {
        return new Cursor(row.itinerary().price().amount(), row.id());
    }

    public String encode() {
        String plain = price.toPlainString() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = plain.lastIndexOf(':');
            if (colon < 1) {
                throw new IllegalArgumentException("missing separator");
            }
            return new Cursor(new BigDecimal(plain.substring(0, colon)), Long.parseLong(plain.substring(colon + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }
}
