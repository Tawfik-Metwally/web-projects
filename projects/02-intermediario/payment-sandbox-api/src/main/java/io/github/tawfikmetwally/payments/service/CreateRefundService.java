package io.github.tawfikmetwally.payments.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreateRefundService {

    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT =
            "uq_idempotency_records_merchant_operation_key";
    private static final String REFUND_UNIQUE_CONSTRAINT =
            "uq_refunds_payment";

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
            if (hasConstraint(exception, IDEMPOTENCY_UNIQUE_CONSTRAINT)) {
                return refundTransaction.replay(command, requestHash);
            }
            if (hasConstraint(exception, REFUND_UNIQUE_CONSTRAINT)) {
                return refundTransaction.resolveRefundConflict(
                        command,
                        requestHash);
            }
            throw exception;
        }
    }

    private boolean hasConstraint(
            Throwable exception,
            String expectedConstraint) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && expectedConstraint.equals(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
