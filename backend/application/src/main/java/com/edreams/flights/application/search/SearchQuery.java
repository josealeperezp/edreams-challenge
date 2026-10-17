package com.edreams.flights.application.search;

import java.math.BigDecimal;

import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.Money;

/** Validated search filters, cursor and page size. Null filters mean "any". */
public record SearchQuery(
        AirportCode origin,
        AirportCode destination,
        Money maxPrice,
        Carrier carrier,
        Cursor cursor,
        int limit) {

    public static final int DEFAULT_LIMIT = 20;

    public SearchQuery {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static SearchQuery of(String origin, String destination, BigDecimal maxPrice, String carrier,
            String cursor, Integer limit, int maxLimit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        if (maxPrice != null && maxPrice.signum() <= 0) {
            throw new IllegalArgumentException("maxPrice must be positive");
        }
        return new SearchQuery(
                blank(origin) ? null : AirportCode.of(origin),
                blank(destination) ? null : AirportCode.of(destination),
                maxPrice == null ? null : Money.eur(maxPrice),
                blank(carrier) ? null : Carrier.of(carrier),
                blank(cursor) ? null : Cursor.decode(cursor),
                size);
    }

    /** Stable string of the four filters only; the key of stats, which ignore cursor and page size. */
    public String filtersKey() {
        return String.join("|",
                origin == null ? "" : origin.value(),
                destination == null ? "" : destination.value(),
                maxPrice == null ? "" : maxPrice.amount().toPlainString(),
                carrier == null ? "" : carrier.code());
    }

    /** Stable string of every field, the second half of a page's cache key after the inventory version. */
    public String cacheKey() {
        return filtersKey() + "|" + (cursor == null ? "" : cursor.encode()) + "|" + limit;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
