package io.github.tawfikmetwally.payments.simulator;

import io.github.tawfikmetwally.payments.enums.PaymentDecision;
import io.github.tawfikmetwally.payments.exception.UnsupportedPaymentMethodTokenException;

public final class DeterministicPaymentSimulator {

    private static final String APPROVED_TOKEN = "tok_approved";
    private static final String DECLINED_TOKEN = "tok_declined";

    public PaymentDecision decide(String paymentMethodToken) {
        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new UnsupportedPaymentMethodTokenException();
        }

        return switch (paymentMethodToken) {
            case APPROVED_TOKEN -> PaymentDecision.APPROVE;
            case DECLINED_TOKEN -> PaymentDecision.DECLINE;
            default -> throw new UnsupportedPaymentMethodTokenException();
        };
    }
}
