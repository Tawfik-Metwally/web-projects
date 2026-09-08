package io.github.tawfikmetwally.payments.exception;

public final class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException() {
        super("Payment was not found");
    }
}
