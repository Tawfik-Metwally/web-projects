package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.entity.RefundEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.enums.RefundStatus;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.PaymentNotRefundableException;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

class CreateRefundTransactionTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.parse("2026-09-09T15:00:00Z");

    private PaymentJpaRepository paymentRepository;
    private RefundJpaRepository refundRepository;
    private PaymentEventJpaRepository paymentEventRepository;
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;
    private final CreateRefundRequestHasher requestHasher =
            new CreateRefundRequestHasher();
    private CreateRefundTransaction refundTransaction;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        refundRepository = mock(RefundJpaRepository.class);
        paymentEventRepository = mock(PaymentEventJpaRepository.class);
        idempotencyRecordRepository = mock(IdempotencyRecordJpaRepository.class);
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRepository.save(any(RefundEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        refundTransaction = new CreateRefundTransaction(
                paymentRepository,
                refundRepository,
                paymentEventRepository,
                idempotencyRecordRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsFullRefundAndIdempotencyRecord() {
        CreateRefundCommand command = command("CUSTOMER_REQUEST");
        stubNoRecord(command);
        when(paymentRepository.findByIdAndMerchantId(
                PAYMENT_ID,
                command.merchantId()))
                .thenReturn(Optional.of(paymentEntity(PaymentStatus.APPROVED)));

        CreateRefundResult result = refundTransaction.execute(
                command,
                requestHasher.hash(command));

        assertThat(result.replayed()).isFalse();
        assertThat(result.refund().getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.refund().getMoney().amountMinor()).isEqualTo(10_000);
        assertThat(result.refund().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.refund().getReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(result.refund().getCreatedAt()).isEqualTo(NOW);

        ArgumentCaptor<PaymentEntity> paymentCaptor =
                ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentCaptor.getValue().getUpdatedAt()).isEqualTo(NOW);

        ArgumentCaptor<RefundEntity> refundCaptor =
                ArgumentCaptor.forClass(RefundEntity.class);
        verify(refundRepository).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getAmountMinor()).isEqualTo(10_000);
        assertThat(refundCaptor.getValue().getPayment().getId())
                .isEqualTo(PAYMENT_ID);

        ArgumentCaptor<PaymentEventEntity> eventCaptor =
                ArgumentCaptor.forClass(PaymentEventEntity.class);
        verify(paymentEventRepository).save(eventCaptor.capture());
        PaymentEventEntity event = eventCaptor.getValue();
        assertThat(event.getPayment().getId()).isEqualTo(PAYMENT_ID);
        assertThat(event.getEventType())
                .isEqualTo(PaymentEventType.PAYMENT_REFUNDED);
        assertThat(event.getFromStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(event.getToStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(event.getOccurredAt()).isEqualTo(NOW);

        ArgumentCaptor<IdempotencyRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(idempotencyRecordRepository).saveAndFlush(recordCaptor.capture());
        IdempotencyRecordEntity record = recordCaptor.getValue();
        assertThat(record.getOperationType())
                .isEqualTo(IdempotencyOperation.CREATE_REFUND);
        assertThat(record.getRequestHash()).isEqualTo(requestHasher.hash(command));
        assertThat(record.getPayment().getId()).isEqualTo(PAYMENT_ID);
        assertThat(record.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void replaysExistingRefundBeforeCheckingCurrentPaymentState() {
        CreateRefundCommand command = command("CUSTOMER_REQUEST");
        String requestHash = requestHasher.hash(command);
        PaymentEntity payment = paymentEntity(PaymentStatus.REFUNDED);
        RefundEntity refund = refundEntity(payment);
        stubRecord(command, existingRecord(command, requestHash, payment));
        when(refundRepository.findByPayment_IdAndPayment_MerchantId(
                PAYMENT_ID,
                command.merchantId()))
                .thenReturn(Optional.of(refund));

        CreateRefundResult result = refundTransaction.execute(command, requestHash);

        assertThat(result.replayed()).isTrue();
        assertThat(result.refund()).usingRecursiveComparison()
                .isEqualTo(refund.toDomain());
        verifyNoInteractions(paymentRepository, paymentEventRepository);
        verifyOnlyRecordLookup(command);
        verify(refundRepository).findByPayment_IdAndPayment_MerchantId(
                PAYMENT_ID,
                command.merchantId());
        verifyNoMoreInteractions(refundRepository);
    }

    @Test
    void rejectsChangedReasonForExistingIdempotencyKeyWithoutWriting() {
        CreateRefundCommand originalCommand = command("CUSTOMER_REQUEST");
        CreateRefundCommand changedCommand = command("DUPLICATE_CHARGE");
        PaymentEntity payment = paymentEntity(PaymentStatus.REFUNDED);
        stubRecord(changedCommand, existingRecord(
                originalCommand,
                requestHasher.hash(originalCommand),
                payment));

        assertThatThrownBy(() -> refundTransaction.execute(
                changedCommand,
                requestHasher.hash(changedCommand)))
                .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(
                paymentRepository,
                refundRepository,
                paymentEventRepository);
        verifyOnlyRecordLookup(changedCommand);
    }

    @Test
    void hidesMissingOrOtherMerchantPaymentAsNotFound() {
        CreateRefundCommand command = command("CUSTOMER_REQUEST");
        stubNoRecord(command);
        when(paymentRepository.findByIdAndMerchantId(
                PAYMENT_ID,
                command.merchantId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundTransaction.execute(
                command,
                requestHasher.hash(command)))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository).findByIdAndMerchantId(
                PAYMENT_ID,
                command.merchantId());
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(refundRepository, paymentEventRepository);
        verifyOnlyRecordLookup(command);
    }

    @Test
    void rejectsOwnNonRefundablePaymentWithoutWriting() {
        CreateRefundCommand command = command("CUSTOMER_REQUEST");
        stubNoRecord(command);
        when(paymentRepository.findByIdAndMerchantId(
                PAYMENT_ID,
                command.merchantId()))
                .thenReturn(Optional.of(paymentEntity(PaymentStatus.DECLINED)));

        assertThatThrownBy(() -> refundTransaction.execute(
                command,
                requestHasher.hash(command)))
                .isInstanceOf(PaymentNotRefundableException.class);

        verify(paymentRepository).findByIdAndMerchantId(
                PAYMENT_ID,
                command.merchantId());
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(refundRepository, paymentEventRepository);
        verifyOnlyRecordLookup(command);
    }

    private void stubNoRecord(CreateRefundCommand command) {
        when(idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        command.merchantId(),
                        IdempotencyOperation.CREATE_REFUND,
                        command.idempotencyKey()))
                .thenReturn(Optional.empty());
    }

    private void stubRecord(
            CreateRefundCommand command,
            IdempotencyRecordEntity record) {
        when(idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        command.merchantId(),
                        IdempotencyOperation.CREATE_REFUND,
                        command.idempotencyKey()))
                .thenReturn(Optional.of(record));
    }

    private void verifyOnlyRecordLookup(CreateRefundCommand command) {
        verify(idempotencyRecordRepository)
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        command.merchantId(),
                        IdempotencyOperation.CREATE_REFUND,
                        command.idempotencyKey());
        verifyNoMoreInteractions(idempotencyRecordRepository);
    }

    private IdempotencyRecordEntity existingRecord(
            CreateRefundCommand command,
            String requestHash,
            PaymentEntity payment) {
        return new IdempotencyRecordEntity(
                UUID.randomUUID(),
                command.merchantId(),
                IdempotencyOperation.CREATE_REFUND,
                command.idempotencyKey(),
                requestHash,
                payment,
                NOW.minusSeconds(60));
    }

    private PaymentEntity paymentEntity(PaymentStatus status) {
        Instant createdAt = NOW.minusSeconds(300);
        return new PaymentEntity(
                PAYMENT_ID,
                "merchant-a",
                "ORDER-123",
                10_000,
                "BRL",
                status,
                createdAt,
                createdAt);
    }

    private RefundEntity refundEntity(PaymentEntity payment) {
        return new RefundEntity(
                UUID.randomUUID(),
                payment,
                10_000,
                RefundStatus.COMPLETED,
                "CUSTOMER_REQUEST",
                NOW.minusSeconds(60));
    }

    private CreateRefundCommand command(String reason) {
        return new CreateRefundCommand(
                "merchant-a",
                "refund-key-001",
                PAYMENT_ID,
                reason);
    }
}
