package io.github.tawfikmetwally.payments.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.entity.RefundEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.InvalidPaymentStateTransitionException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.PaymentNotRefundableException;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@Service
public class CreateRefundTransaction {

    private final PaymentJpaRepository paymentRepository;
    private final RefundJpaRepository refundRepository;
    private final PaymentEventJpaRepository paymentEventRepository;
    private final IdempotencyRecordJpaRepository idempotencyRecordRepository;
    private final Clock clock;

    public CreateRefundTransaction(
            PaymentJpaRepository paymentRepository,
            RefundJpaRepository refundRepository,
            PaymentEventJpaRepository paymentEventRepository,
            IdempotencyRecordJpaRepository idempotencyRecordRepository,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.clock = clock;
    }

    @Transactional
    public CreateRefundResult execute(CreateRefundCommand command, String requestHash) {
        Optional<IdempotencyRecordEntity> existingRecord = findRecord(command);
        if (existingRecord.isPresent()) {
            return toReplayResult(existingRecord.get(), command, requestHash);
        }

        PaymentEntity paymentEntity = paymentRepository
                .findByIdAndMerchantId(command.paymentId(), command.merchantId())
                .orElseThrow(PaymentNotFoundException::new);
        Payment payment = paymentEntity.toDomain();
        PaymentStatus previousStatus = payment.getStatus();
        Instant occurredAt = clock.instant();
        Refund refund;
        try {
            refund = payment.refund(
                    UUID.randomUUID(),
                    command.reason(),
                    occurredAt);
        } catch (InvalidPaymentStateTransitionException exception) {
            throw new PaymentNotRefundableException();
        }

        PaymentEntity updatedPayment = paymentRepository.save(
                PaymentEntity.fromDomain(payment));
        refundRepository.save(RefundEntity.fromDomain(refund, updatedPayment));
        paymentEventRepository.save(refundedEvent(
                updatedPayment,
                previousStatus,
                occurredAt));
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecordEntity(
                UUID.randomUUID(),
                command.merchantId(),
                IdempotencyOperation.CREATE_REFUND,
                command.idempotencyKey(),
                requestHash,
                updatedPayment,
                occurredAt));

        return new CreateRefundResult(refund, false);
    }

    @Transactional(readOnly = true)
    public CreateRefundResult replay(
            CreateRefundCommand command,
            String requestHash) {
        IdempotencyRecordEntity record = findRecord(command)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency winner was not found after unique constraint conflict"));
        return toReplayResult(record, command, requestHash);
    }

    private Optional<IdempotencyRecordEntity> findRecord(
            CreateRefundCommand command) {
        return idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        command.merchantId(),
                        IdempotencyOperation.CREATE_REFUND,
                        command.idempotencyKey());
    }

    private CreateRefundResult toReplayResult(
            IdempotencyRecordEntity record,
            CreateRefundCommand command,
            String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }

        Refund refund = refundRepository
                .findByPayment_IdAndPayment_MerchantId(
                        record.getPayment().getId(),
                        command.merchantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Refund was not found for an existing idempotency record"))
                .toDomain();
        return new CreateRefundResult(refund, true);
    }

    private PaymentEventEntity refundedEvent(
            PaymentEntity payment,
            PaymentStatus previousStatus,
            Instant occurredAt) {
        return new PaymentEventEntity(
                UUID.randomUUID(),
                payment,
                PaymentEventType.PAYMENT_REFUNDED,
                previousStatus,
                payment.getStatus(),
                occurredAt);
    }
}
