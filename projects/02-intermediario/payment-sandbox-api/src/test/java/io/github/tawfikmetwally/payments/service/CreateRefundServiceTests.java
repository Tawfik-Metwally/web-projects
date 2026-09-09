package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.enums.RefundStatus;

class CreateRefundServiceTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");
    private static final String IDEMPOTENCY_CONSTRAINT =
            "uq_idempotency_records_merchant_operation_key";
    private static final Instant NOW = Instant.parse("2026-09-09T15:00:00Z");

    private CreateRefundTransaction refundTransaction;
    private CreateRefundRequestHasher requestHasher;
    private CreateRefundService service;

    @BeforeEach
    void setUp() {
        refundTransaction = mock(CreateRefundTransaction.class);
        requestHasher = new CreateRefundRequestHasher();
        service = new CreateRefundService(refundTransaction, requestHasher);
    }

    @Test
    void returnsResultFromSuccessfulTransaction() {
        CreateRefundCommand command = command();
        String requestHash = requestHasher.hash(command);
        CreateRefundResult created = new CreateRefundResult(refund(), false);
        when(refundTransaction.execute(command, requestHash)).thenReturn(created);

        CreateRefundResult result = service.create(command);

        assertThat(result).isSameAs(created);
        verify(refundTransaction).execute(command, requestHash);
        verifyNoMoreInteractions(refundTransaction);
    }

    @Test
    void replaysWinnerAfterIdempotencyConstraintConflict() {
        CreateRefundCommand command = command();
        String requestHash = requestHasher.hash(command);
        DataIntegrityViolationException databaseException =
                databaseException(IDEMPOTENCY_CONSTRAINT);
        CreateRefundResult replayed = new CreateRefundResult(refund(), true);
        when(refundTransaction.execute(command, requestHash))
                .thenThrow(databaseException);
        when(refundTransaction.replay(command, requestHash))
                .thenReturn(replayed);

        CreateRefundResult result = service.create(command);

        assertThat(result).isSameAs(replayed);
        verify(refundTransaction).execute(command, requestHash);
        verify(refundTransaction).replay(command, requestHash);
        verifyNoMoreInteractions(refundTransaction);
    }

    @Test
    void propagatesViolationFromAnotherDatabaseConstraint() {
        CreateRefundCommand command = command();
        String requestHash = requestHasher.hash(command);
        DataIntegrityViolationException databaseException =
                databaseException("uq_refunds_payment");
        when(refundTransaction.execute(command, requestHash))
                .thenThrow(databaseException);

        assertThatThrownBy(() -> service.create(command))
                .isSameAs(databaseException);

        verify(refundTransaction).execute(command, requestHash);
        verifyNoMoreInteractions(refundTransaction);
    }

    private DataIntegrityViolationException databaseException(
            String constraintName) {
        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException(
                "Database constraint violation",
                constraintViolation);
    }

    private CreateRefundCommand command() {
        return new CreateRefundCommand(
                "merchant-a",
                "refund-key-001",
                PAYMENT_ID,
                "CUSTOMER_REQUEST");
    }

    private Refund refund() {
        return Refund.restore(
                UUID.randomUUID(),
                PAYMENT_ID,
                new Money(10_000, Currency.getInstance("BRL")),
                RefundStatus.COMPLETED,
                "CUSTOMER_REQUEST",
                NOW);
    }
}
