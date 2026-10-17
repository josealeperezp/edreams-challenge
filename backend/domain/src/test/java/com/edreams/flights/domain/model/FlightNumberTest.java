package com.edreams.flights.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FlightNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"845", "0845", " 845 ", "08 45"})
    void parsesProviderRepresentationsToTheSameNumber(String raw) {
        assertThat(FlightNumber.parse(raw)).isEqualTo(new FlightNumber(845));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "IB845", "12345", "-1"})
    void rejectsInvalidNumbers(String raw) {
        assertThatThrownBy(() -> FlightNumber.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
