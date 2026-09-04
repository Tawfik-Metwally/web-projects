package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.entity.RefundEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.enums.RefundStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class PersistenceIntegrationTests {

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentEventJpaRepository paymentEventRepository;

    @Autowired
    private RefundJpaRepository refundRepository;

    @Autowired
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

    @Test
    void findsPaymentOnlyForItsMerchant() {
        PaymentEntity payment = savePayment("merchant-a", "order-001");

        assertThat(paymentRepository.findByIdAndMerchantId(payment.getId(), "merchant-a"))
                .isPresent()
                .get()
                .extracting(PaymentEntity::getMerchantReference)
                .isEqualTo("order-001");
        assertThat(paymentRepository.findByIdAndMerchantId(payment.getId(), "merchant-b"))
                .isEmpty();
    }

    @Test
    void returnsPaymentEventsInChronologicalOrderAndOnlyForItsMerchant() {
        PaymentEntity payment = savePayment("merchant-a", "order-002");
        Instant createdAt = Instant.parse("2026-09-01T10:00:00Z");
        Instant approvedAt = createdAt.plusSeconds(15 * 60);
        Instant refundedAt = createdAt.plusSeconds(30 * 60);

        paymentEventRepository.saveAllAndFlush(List.of(
                new PaymentEventEntity(
                        UUID.randomUUID(),
                        payment,
                        PaymentEventType.PAYMENT_REFUNDED,
                        PaymentStatus.APPROVED,
                        PaymentStatus.REFUNDED,
                        refundedAt),
                new PaymentEventEntity(
                        UUID.randomUUID(),
                        payment,
                        PaymentEventType.PAYMENT_CREATED,
                        null,
                        PaymentStatus.PENDING,
                        createdAt),
                new PaymentEventEntity(
                        UUID.randomUUID(),
                        payment,
                        PaymentEventType.PAYMENT_APPROVED,
                        PaymentStatus.PENDING,
                        PaymentStatus.APPROVED,
                        approvedAt)));

        List<PaymentEventEntity> events = paymentEventRepository
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        payment.getId(),
                        "merchant-a");

        assertThat(events)
                .extracting(PaymentEventEntity::getOccurredAt)
                .containsExactly(createdAt, approvedAt, refundedAt);
        assertThat(paymentEventRepository
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        payment.getId(),
                        "merchant-b"))
                .isEmpty();
    }

    @Test
    void rejectsASecondRefundForTheSamePayment() {
        PaymentEntity payment = savePayment("merchant-a", "order-003");
        Instant createdAt = Instant.parse("2026-09-01T11:00:00Z");

        refundRepository.saveAndFlush(new RefundEntity(
                UUID.randomUUID(),
                payment,
                payment.getAmountMinor(),
                RefundStatus.COMPLETED,
                "Customer request",
                createdAt));

        assertThat(refundRepository.findByPayment_IdAndPayment_MerchantId(
                payment.getId(),
                "merchant-a"))
                .isPresent();
        assertThat(refundRepository.findByPayment_IdAndPayment_MerchantId(
                payment.getId(),
                "merchant-b"))
                .isEmpty();

        RefundEntity duplicateRefund = new RefundEntity(
                UUID.randomUUID(),
                payment,
                payment.getAmountMinor(),
                RefundStatus.COMPLETED,
                "Duplicated request",
                createdAt.plusSeconds(60));

        assertThatThrownBy(() -> refundRepository.saveAndFlush(duplicateRefund))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scopesIdempotencyKeyByMerchantAndOperationAndRejectsExactDuplicate() {
        PaymentEntity merchantAPayment = savePayment("merchant-a", "order-004");
        PaymentEntity merchantBPayment = savePayment("merchant-b", "order-005");
        Instant createdAt = Instant.parse("2026-09-01T12:00:00Z");
        String sharedKey = "key-001";

        idempotencyRecordRepository.saveAllAndFlush(List.of(
                idempotencyRecord(
                        "merchant-a",
                        IdempotencyOperation.CREATE_PAYMENT,
                        sharedKey,
                        "a".repeat(64),
                        merchantAPayment,
                        createdAt),
                idempotencyRecord(
                        "merchant-b",
                        IdempotencyOperation.CREATE_PAYMENT,
                        sharedKey,
                        "b".repeat(64),
                        merchantBPayment,
                        createdAt),
                idempotencyRecord(
                        "merchant-a",
                        IdempotencyOperation.CREATE_REFUND,
                        sharedKey,
                        "c".repeat(64),
                        merchantAPayment,
                        createdAt)));

        assertThat(idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        "merchant-a",
                        IdempotencyOperation.CREATE_PAYMENT,
                        sharedKey))
                .isPresent();
        assertThat(idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        "merchant-b",
                        IdempotencyOperation.CREATE_PAYMENT,
                        sharedKey))
                .isPresent();
        assertThat(idempotencyRecordRepository
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        "merchant-a",
                        IdempotencyOperation.CREATE_REFUND,
                        sharedKey))
                .isPresent();

        IdempotencyRecordEntity exactDuplicate = idempotencyRecord(
                "merchant-a",
                IdempotencyOperation.CREATE_PAYMENT,
                sharedKey,
                "d".repeat(64),
                merchantAPayment,
                createdAt.plusSeconds(60));

        assertThatThrownBy(() -> idempotencyRecordRepository.saveAndFlush(exactDuplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PaymentEntity savePayment(String merchantId, String merchantReference) {
        Instant now = Instant.parse("2026-09-01T09:00:00Z");
        return paymentRepository.saveAndFlush(new PaymentEntity(
                UUID.randomUUID(),
                merchantId,
                merchantReference,
                10_000,
                "BRL",
                PaymentStatus.APPROVED,
                now,
                now));
    }

    private IdempotencyRecordEntity idempotencyRecord(
            String merchantId,
            IdempotencyOperation operation,
            String key,
            String requestHash,
            PaymentEntity payment,
            Instant createdAt) {
        return new IdempotencyRecordEntity(
                UUID.randomUUID(),
                merchantId,
                operation,
                key,
                requestHash,
                payment,
                createdAt);
    }
}
