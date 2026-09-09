package io.github.tawfikmetwally.payments.exception;

public final class PaymentNotRefundableException extends RuntimeException {

    public PaymentNotRefundableException() {
        super("Payment is not refundable in its current state");
    }
}
