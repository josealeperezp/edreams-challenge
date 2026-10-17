package com.edreams.flights.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesToScaleTwoHalfUp() {
        assertThat(Money.eur("89.775").amount()).isEqualByComparingTo("89.78");
        assertThat(Money.eur("89.774").amount()).isEqualByComparingTo("89.77");
        assertThat(Money.eur(BigDecimal.valueOf(89.9)).amount().scale()).isEqualTo(2);
    }

    @Test
    void comparesAmountsInTheSameCurrency() {
        assertThat(Money.eur("85.50").isCheaperThan(Money.eur("89.90"))).isTrue();
        assertThat(Money.eur("89.90").isCheaperThan(Money.eur("89.90"))).isFalse();
        assertThat(Money.eur("0.00").isPositive()).isFalse();
    }

    @Test
    void refusesToCompareDifferentCurrencies() {
        Money usd = new Money(new BigDecimal("10"), Currency.getInstance("USD"));
        assertThatThrownBy(() -> usd.isCheaperThan(Money.eur("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }
}
