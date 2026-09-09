package io.github.tawfikmetwally.payments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.enums.RefundStatus;

class RefundEntityMappingTests {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Instant CREATED_AT = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void convertsFullRefundToPersistenceFields() {
        Payment payment = approvedPayment();
        UUID refundId = UUID.randomUUID();
        Instant refundedAt = CREATED_AT.plusSeconds(60);
        Refund refund = payment.refund(
                refundId,
                "CUSTOMER_REQUEST",
                refundedAt);
        PaymentEntity paymentEntity = PaymentEntity.fromDomain(payment);

        RefundEntity entity = RefundEntity.fromDomain(refund, paymentEntity);

        assertThat(entity.getId()).isEqualTo(refundId);
        assertThat(entity.getPayment().getId()).isEqualTo(payment.getId());
        assertThat(entity.getAmountMinor()).isEqualTo(10_000);
        assertThat(entity.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(entity.getReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(entity.getCreatedAt()).isEqualTo(refundedAt);
    }

    @Test
    void restoresFullRefundFromPersistenceFields() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Instant refundedAt = CREATED_AT.plusSeconds(60);
        PaymentEntity paymentEntity = new PaymentEntity(
                paymentId,
                "merchant-a",
                "ORDER-123",
                10_000,
                "BRL",
                PaymentStatus.REFUNDED,
                CREATED_AT,
                refundedAt);
        RefundEntity entity = new RefundEntity(
                refundId,
                paymentEntity,
                10_000,
                RefundStatus.COMPLETED,
                "CUSTOMER_REQUEST",
                refundedAt);

        Refund refund = entity.toDomain();

        assertThat(refund.getId()).isEqualTo(refundId);
        assertThat(refund.getPaymentId()).isEqualTo(paymentId);
        assertThat(refund.getMoney()).isEqualTo(new Money(10_000, BRL));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(refund.getCreatedAt()).isEqualTo(refundedAt);
    }

    private Payment approvedPayment() {
        Payment payment = Payment.create(
                UUID.randomUUID(),
                "merchant-a",
                "ORDER-123",
                new Money(10_000, BRL),
                CREATED_AT);
        payment.approve(CREATED_AT);
        return payment;
    }
}
