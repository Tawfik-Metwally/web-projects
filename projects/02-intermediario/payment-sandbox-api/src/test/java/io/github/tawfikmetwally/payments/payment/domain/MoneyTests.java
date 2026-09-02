package io.github.tawfikmetwally.payments.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTests {

    private static final Currency BRL = Currency.getInstance("BRL");

    @Test
    void createsMoneyWithPositiveAmountAndBrl() {
        Money money = new Money(10_000, BRL);

        assertThat(money.amountMinor()).isEqualTo(10_000);
        assertThat(money.currency()).isEqualTo(BRL);
    }

    @Test
    void comparesMoneyByValue() {
        Money first = new Money(10_000, BRL);
        Money second = new Money(10_000, BRL);
        Money different = new Money(15_000, BRL);

        assertThat(first)
                .isEqualTo(second)
                .isNotEqualTo(different);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveAmount(long invalidAmount) {
        assertThatThrownBy(() -> new Money(invalidAmount, BRL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amountMinor must be greater than zero");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(10_000, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency must not be null");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        Currency usd = Currency.getInstance("USD");

        assertThatThrownBy(() -> new Money(10_000, usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency must be BRL");
    }
}
