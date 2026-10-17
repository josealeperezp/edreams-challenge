package com.edreams.flights.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CursorTest {

    @Test
    void roundTripsThroughAnOpaqueString() {
        Cursor cursor = new Cursor(new BigDecimal("89.78"), 12345L);
        String encoded = cursor.encode();

        assertThat(encoded).doesNotContain(":", "=", "/", "+");
        assertThat(Cursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void pointsAfterTheLastRowServed() {
        ItineraryRow row = TestRows.row(42L, "85.50");
        assertThat(Cursor.after(row)).isEqualTo(new Cursor(new BigDecimal("89.78"), 42L));
    }

    @Test
    void rejectsTamperedOrForeignValues() {
        assertThatThrownBy(() -> Cursor.decode("not base64 at all!")).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> Cursor.decode("aGVsbG8")).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> Cursor.decode("")).isInstanceOf(IllegalArgumentException.class);
    }
}
