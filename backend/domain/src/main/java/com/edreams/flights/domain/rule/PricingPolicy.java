package com.edreams.flights.domain.rule;

import java.math.BigDecimal;
import java.util.Objects;

import com.edreams.flights.domain.model.Money;

/** The OTA operational markup applied to the cheapest provider price. */
public record PricingPolicy(BigDecimal markupPercent) {

    public PricingPolicy {
        Objects.requireNonNull(markupPercent, "markupPercent");
        if (markupPercent.signum() < 0) {
            throw new IllegalArgumentException("Markup cannot be negative: " + markupPercent);
        }
    }

    public static PricingPolicy ofPercent(String percent) {
        return new PricingPolicy(new BigDecimal(percent));
    }

    public Money apply(Money base) {
        return base.plusPercent(markupPercent);
    }
}
