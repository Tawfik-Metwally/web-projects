package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CreateRefundRequestHasherTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");

    private final CreateRefundRequestHasher hasher =
            new CreateRefundRequestHasher();

    @Test
    void returnsSameHashForEquivalentCommands() {
        assertThat(hasher.hash(command(PAYMENT_ID, "CUSTOMER_REQUEST")))
                .isEqualTo(hasher.hash(command(PAYMENT_ID, "CUSTOMER_REQUEST")));
    }

    @Test
    void returns64LowercaseHexadecimalCharacters() {
        assertThat(hasher.hash(command(PAYMENT_ID, "CUSTOMER_REQUEST")))
                .matches("[0-9a-f]{64}");
    }

    @ParameterizedTest
    @MethodSource("commandsWithChangedRefundData")
    void changesHashWhenAnyRefundFieldChanges(
            CreateRefundCommand changedCommand) {
        String originalHash = hasher.hash(
                command(PAYMENT_ID, "CUSTOMER_REQUEST"));

        assertThat(hasher.hash(changedCommand)).isNotEqualTo(originalHash);
    }

    @Test
    void excludesMerchantAndIdempotencyKeyFromPayloadHash() {
        CreateRefundCommand otherScope = new CreateRefundCommand(
                "merchant-b",
                "another-key",
                PAYMENT_ID,
                "CUSTOMER_REQUEST");

        assertThat(hasher.hash(otherScope))
                .isEqualTo(hasher.hash(command(PAYMENT_ID, "CUSTOMER_REQUEST")));
    }

    private static Stream<CreateRefundCommand> commandsWithChangedRefundData() {
        return Stream.of(
                command(UUID.randomUUID(), "CUSTOMER_REQUEST"),
                command(PAYMENT_ID, "DUPLICATE_CHARGE"));
    }

    private static CreateRefundCommand command(UUID paymentId, String reason) {
        return new CreateRefundCommand(
                "merchant-a",
                "refund-key-001",
                paymentId,
                reason);
    }
}
