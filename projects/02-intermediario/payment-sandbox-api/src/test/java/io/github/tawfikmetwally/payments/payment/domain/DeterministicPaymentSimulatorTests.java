package io.github.tawfikmetwally.payments.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeterministicPaymentSimulatorTests {

    private final DeterministicPaymentSimulator simulator =
            new DeterministicPaymentSimulator();

    @Test
    void approvesApprovedToken() {
        PaymentDecision decision = simulator.decide("tok_approved");

        assertThat(decision).isEqualTo(PaymentDecision.APPROVE);
    }

    @Test
    void declinesDeclinedToken() {
        PaymentDecision decision = simulator.decide("tok_declined");

        assertThat(decision).isEqualTo(PaymentDecision.DECLINE);
    }

    @Test
    void returnsSameDecisionForRepeatedToken() {
        PaymentDecision first = simulator.decide("tok_approved");
        PaymentDecision second = simulator.decide("tok_approved");

        assertThat(first)
                .isEqualTo(PaymentDecision.APPROVE)
                .isEqualTo(second);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "tok_unknown"})
    void rejectsUnsupportedToken(String unsupportedToken) {
        assertThatThrownBy(() -> simulator.decide(unsupportedToken))
                .isInstanceOf(UnsupportedPaymentMethodTokenException.class)
                .hasMessage("Unsupported payment method token");
    }
}
