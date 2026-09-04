package io.github.tawfikmetwally.payments.exception;

public final class UnsupportedPaymentMethodTokenException extends IllegalArgumentException {

    public UnsupportedPaymentMethodTokenException() {
        super("Unsupported payment method token");
    }
}
