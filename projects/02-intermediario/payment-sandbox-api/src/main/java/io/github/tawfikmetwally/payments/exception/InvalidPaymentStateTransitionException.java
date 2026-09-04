package io.github.tawfikmetwally.payments.exception;

import java.util.Objects;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;

public final class InvalidPaymentStateTransitionException extends IllegalStateException {

    private final PaymentStatus currentStatus;
    private final PaymentStatus targetStatus;

    public InvalidPaymentStateTransitionException(
            PaymentStatus currentStatus,
            PaymentStatus targetStatus) {
        super("Cannot transition payment from " + currentStatus + " to " + targetStatus);
        this.currentStatus = Objects.requireNonNull(
                currentStatus,
                "currentStatus must not be null");
        this.targetStatus = Objects.requireNonNull(
                targetStatus,
                "targetStatus must not be null");
    }

    public PaymentStatus getCurrentStatus() {
        return currentStatus;
    }

    public PaymentStatus getTargetStatus() {
        return targetStatus;
    }
}
