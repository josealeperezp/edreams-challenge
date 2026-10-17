package com.edreams.flights.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AirportCodeTest {

    @Test
    void normalisesProviderInput() {
        assertThat(AirportCode.of("  mad ")).isEqualTo(new AirportCode("MAD"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "MA", "MADR", "M4D", "mad"})
    void rejectsInvalidCodes(String raw) {
        assertThatThrownBy(() -> new AirportCode(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IATA airport code");
    }
}
