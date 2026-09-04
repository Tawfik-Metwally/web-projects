package io.github.tawfikmetwally.payments.domain;

import java.util.Currency;
import java.util.Objects;

public record Money(long amountMinor, Currency currency) {

    private static final Currency BRL = Currency.getInstance("BRL");

    public Money {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be greater than zero");
        }

        Objects.requireNonNull(currency, "currency must not be null");
        if (!BRL.equals(currency)) {
            throw new IllegalArgumentException("currency must be BRL");
        }
    }
}
