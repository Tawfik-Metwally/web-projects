package io.github.tawfikmetwally.payments.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreatePaymentService {

    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT =
            "uq_idempotency_records_merchant_operation_key";

    private final CreatePaymentTransaction paymentTransaction;
    private final CreatePaymentRequestHasher requestHasher;

    public CreatePaymentService(
            CreatePaymentTransaction paymentTransaction,
            CreatePaymentRequestHasher requestHasher) {
        this.paymentTransaction = paymentTransaction;
        this.requestHasher = requestHasher;
    }

    public CreatePaymentResult create(CreatePaymentCommand command) {
        String requestHash = requestHasher.hash(command);

        try {
            return paymentTransaction.execute(command, requestHash);
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyKeyConflict(exception)) {
                throw exception;
            }

            return paymentTransaction.replay(command, requestHash);
        }
    }

    private boolean isIdempotencyKeyConflict(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && IDEMPOTENCY_UNIQUE_CONSTRAINT.equals(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
