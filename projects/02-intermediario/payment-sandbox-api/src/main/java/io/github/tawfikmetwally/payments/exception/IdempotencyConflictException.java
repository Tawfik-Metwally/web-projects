package io.github.tawfikmetwally.payments.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key was already used with different payment data");
    }
}
