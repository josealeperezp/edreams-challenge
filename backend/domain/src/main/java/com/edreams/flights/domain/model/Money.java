package com.edreams.flights.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** An amount with its currency, always at scale 2 with HALF_UP rounding. */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency EUR = Currency.getInstance("EUR");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money eur(String amount) {
        return new Money(new BigDecimal(amount), EUR);
    }

    public static Money eur(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    public Money plusPercent(BigDecimal percent) {
        BigDecimal factor = BigDecimal.ONE.add(percent.divide(HUNDRED));
        return new Money(amount.multiply(factor), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isCheaperThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public int compareAmount(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
