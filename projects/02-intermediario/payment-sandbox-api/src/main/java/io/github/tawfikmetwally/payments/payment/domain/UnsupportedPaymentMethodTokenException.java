package io.github.tawfikmetwally.payments.payment.domain;

public final class UnsupportedPaymentMethodTokenException extends IllegalArgumentException {

    public UnsupportedPaymentMethodTokenException() {
        super("Unsupported payment method token");
    }
}
