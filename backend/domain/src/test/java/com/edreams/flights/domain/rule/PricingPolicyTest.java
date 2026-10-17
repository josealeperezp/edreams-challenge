package com.edreams.flights.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.edreams.flights.domain.model.Money;

class PricingPolicyTest {

    @ParameterizedTest
    @CsvSource({"85.50, 89.78", "100.00, 105.00", "239.00, 250.95", "0.10, 0.11", "64.99, 68.24"})
    void appliesFivePercentHalfUp(String base, String expected) {
        assertThat(PricingPolicy.ofPercent("5").apply(Money.eur(base))).isEqualTo(Money.eur(expected));
    }

    @ParameterizedTest
    @CsvSource({"0, 100.00, 100.00", "12.5, 80.00, 90.00"})
    void markupIsConfigurable(String percent, String base, String expected) {
        assertThat(PricingPolicy.ofPercent(percent).apply(Money.eur(base))).isEqualTo(Money.eur(expected));
    }
}
