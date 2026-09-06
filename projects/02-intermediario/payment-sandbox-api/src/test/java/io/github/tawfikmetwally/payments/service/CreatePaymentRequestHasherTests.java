package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CreatePaymentRequestHasherTests {

    private static final Currency BRL = Currency.getInstance("BRL");

    private final CreatePaymentRequestHasher hasher = new CreatePaymentRequestHasher();

    @Test
    void returnsSameHashForEquivalentCommands() {
        CreatePaymentCommand first = command(10_000, BRL, "ORDER-001", "tok_approved");
        CreatePaymentCommand second = command(10_000, BRL, "ORDER-001", "tok_approved");

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second));
    }

    @Test
    void returns64LowercaseHexadecimalCharacters() {
        String hash = hasher.hash(command(10_000, BRL, "ORDER-001", "tok_approved"));

        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @ParameterizedTest
    @MethodSource("commandsWithChangedPaymentData")
    void changesHashWhenAnyPaymentFieldChanges(CreatePaymentCommand changedCommand) {
        String originalHash = hasher.hash(
                command(10_000, BRL, "ORDER-001", "tok_approved"));

        assertThat(hasher.hash(changedCommand)).isNotEqualTo(originalHash);
    }

    @Test
    void excludesMerchantAndIdempotencyKeyFromPayloadHash() {
        CreatePaymentCommand first = command(10_000, BRL, "ORDER-001", "tok_approved");
        CreatePaymentCommand otherScope = new CreatePaymentCommand(
                "merchant-b", "another-key", 10_000, BRL, "ORDER-001", "tok_approved");

        assertThat(hasher.hash(otherScope)).isEqualTo(hasher.hash(first));
    }

    @Test
    void distinguishesFieldBoundariesEvenWhenValuesContainSeparators() {
        CreatePaymentCommand first = command(10_000, BRL, "ORDER|tok", "approved");
        CreatePaymentCommand second = command(10_000, BRL, "ORDER", "tok|approved");

        assertThat(hasher.hash(first)).isNotEqualTo(hasher.hash(second));
    }

    private static Stream<CreatePaymentCommand> commandsWithChangedPaymentData() {
        return Stream.of(
                command(20_000, BRL, "ORDER-001", "tok_approved"),
                command(10_000, Currency.getInstance("USD"), "ORDER-001", "tok_approved"),
                command(10_000, BRL, "ORDER-002", "tok_approved"),
                command(10_000, BRL, "ORDER-001", "tok_declined"));
    }

    private static CreatePaymentCommand command(
            long amountMinor,
            Currency currency,
            String merchantReference,
            String paymentMethodToken) {
        return new CreatePaymentCommand(
                "merchant-a", "idem-001", amountMinor, currency,
                merchantReference, paymentMethodToken);
    }
}
