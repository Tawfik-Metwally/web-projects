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
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;

class CreatePaymentServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-03T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");
    private static final String IDEMPOTENCY_CONSTRAINT =
            "uq_idempotency_records_merchant_operation_key";

    private CreatePaymentTransaction paymentTransaction;
    private CreatePaymentRequestHasher requestHasher;
    private CreatePaymentService service;

    @BeforeEach
    void setUp() {
        paymentTransaction = mock(CreatePaymentTransaction.class);
        requestHasher = new CreatePaymentRequestHasher();
        service = new CreatePaymentService(paymentTransaction, requestHasher);
    }

    @Test
    void returnsResultFromSuccessfulTransaction() {
        CreatePaymentCommand command = command();
        String requestHash = requestHasher.hash(command);
        CreatePaymentResult created = new CreatePaymentResult(
                payment(PaymentStatus.APPROVED), false);
        when(paymentTransaction.execute(command, requestHash)).thenReturn(created);

        CreatePaymentResult result = service.create(command);

        assertThat(result).isSameAs(created);
        verify(paymentTransaction).execute(command, requestHash);
        verifyNoMoreInteractions(paymentTransaction);
    }

    @Test
    void replaysWinnerAfterIdempotencyConstraintConflict() {
        CreatePaymentCommand command = command();
        String requestHash = requestHasher.hash(command);
        DataIntegrityViolationException databaseException =
                databaseException(IDEMPOTENCY_CONSTRAINT);
        CreatePaymentResult replayed = new CreatePaymentResult(
                payment(PaymentStatus.APPROVED), true);
        when(paymentTransaction.execute(command, requestHash))
                .thenThrow(databaseException);
        when(paymentTransaction.replay(command, requestHash)).thenReturn(replayed);

        CreatePaymentResult result = service.create(command);

        assertThat(result).isSameAs(replayed);
        verify(paymentTransaction).execute(command, requestHash);
        verify(paymentTransaction).replay(command, requestHash);
        verifyNoMoreInteractions(paymentTransaction);
    }

    @Test
    void propagatesViolationFromAnotherDatabaseConstraint() {
        CreatePaymentCommand command = command();
        String requestHash = requestHasher.hash(command);
        DataIntegrityViolationException databaseException =
                databaseException("ck_payments_amount_positive");
        when(paymentTransaction.execute(command, requestHash))
                .thenThrow(databaseException);

        assertThatThrownBy(() -> service.create(command))
                .isSameAs(databaseException);

        verify(paymentTransaction).execute(command, requestHash);
        verifyNoMoreInteractions(paymentTransaction);
    }

    private DataIntegrityViolationException databaseException(String constraintName) {
        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException(
                "Database constraint violation", constraintViolation);
    }

    private CreatePaymentCommand command() {
        return new CreatePaymentCommand(
                "merchant-a",
                "idem-123",
                10_000,
                BRL,
                "ORDER-123",
                "tok_approved");
    }

    private Payment payment(PaymentStatus status) {
        return Payment.restore(
                UUID.randomUUID(),
                "merchant-a",
                "ORDER-123",
                new Money(10_000, BRL),
                status,
                NOW,
                NOW);
    }
}
