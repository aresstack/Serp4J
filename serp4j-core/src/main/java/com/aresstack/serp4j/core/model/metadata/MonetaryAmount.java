package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.math.BigDecimal;
import java.util.Objects;

/** A monetary amount with an ISO-4217 currency code (ADR-004); amounts are never {@code double}. */
public final class MonetaryAmount {

    private final BigDecimal amount;
    private final String currency;

    private MonetaryAmount(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    /** @param currency three-letter ISO-4217 code, case-insensitive */
    public static MonetaryAmount of(BigDecimal amount, String currency) {
        Preconditions.requireNonNull(amount, "amount");
        String code = Preconditions.requireText(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter ISO-4217 code but was '" + currency + "'");
        }
        return new MonetaryAmount(amount, code);
    }

    public static MonetaryAmount of(String amount, String currency) {
        return of(new BigDecimal(Preconditions.requireText(amount, "amount")), currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public MonetaryAmount add(MonetaryAmount other) {
        Preconditions.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot add " + other.currency + " to " + currency);
        }
        return new MonetaryAmount(amount.add(other.amount), currency);
    }

    /** Equality by numeric value ({@code 1.0 == 1.00}) and currency. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonetaryAmount)) {
            return false;
        }
        MonetaryAmount that = (MonetaryAmount) o;
        return amount.compareTo(that.amount) == 0 && currency.equals(that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
