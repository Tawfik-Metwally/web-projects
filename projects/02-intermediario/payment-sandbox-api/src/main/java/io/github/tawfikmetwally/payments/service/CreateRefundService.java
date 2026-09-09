package io.github.tawfikmetwally.payments.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreateRefundService {

    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT =
            "uq_idempotency_records_merchant_operation_key";

    private final CreateRefundTransaction refundTransaction;
    private final CreateRefundRequestHasher requestHasher;

    public CreateRefundService(
            CreateRefundTransaction refundTransaction,
            CreateRefundRequestHasher requestHasher) {
        this.refundTransaction = refundTransaction;
        this.requestHasher = requestHasher;
    }

    public CreateRefundResult create(CreateRefundCommand command) {
        String requestHash = requestHasher.hash(command);

        try {
            return refundTransaction.execute(command, requestHash);
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyKeyConflict(exception)) {
                throw exception;
            }

            return refundTransaction.replay(command, requestHash);
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
