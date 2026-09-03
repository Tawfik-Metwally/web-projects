package io.github.tawfikmetwally.payments.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.tawfikmetwally.payments.payment.domain.Money;
import io.github.tawfikmetwally.payments.payment.domain.Payment;
import io.github.tawfikmetwally.payments.payment.domain.PaymentStatus;

class PaymentEntityMappingTests {

    private static final Currency BRL = Currency.getInstance("BRL");

    @Test
    void convertsDomainPaymentToPersistenceFields() {
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-03T12:00:00Z");
        Instant approvedAt = Instant.parse("2026-09-03T12:00:01Z");
        Payment payment = Payment.create(
                paymentId,
                "merchant-a",
                "ORDER-123",
                new Money(10_000, BRL),
                createdAt);
        payment.approve(approvedAt);

        PaymentEntity entity = PaymentEntity.fromDomain(payment);

        assertThat(entity.getId()).isEqualTo(paymentId);
        assertThat(entity.getMerchantId()).isEqualTo("merchant-a");
        assertThat(entity.getMerchantReference()).isEqualTo("ORDER-123");
        assertThat(entity.getAmountMinor()).isEqualTo(10_000);
        assertThat(entity.getCurrency()).isEqualTo("BRL");
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(approvedAt);
    }

    @Test
    void restoresDomainPaymentFromPersistenceFields() {
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-03T12:00:00Z");
        Instant approvedAt = Instant.parse("2026-09-03T12:00:01Z");
        PaymentEntity entity = new PaymentEntity(
                paymentId,
                "merchant-a",
                "ORDER-123",
                10_000,
                "BRL",
                PaymentStatus.APPROVED,
                createdAt,
                approvedAt);

        Payment payment = entity.toDomain();

        assertThat(payment.getId()).isEqualTo(paymentId);
        assertThat(payment.getMerchantId()).isEqualTo("merchant-a");
        assertThat(payment.getMerchantReference()).isEqualTo("ORDER-123");
        assertThat(payment.getMoney()).isEqualTo(new Money(10_000, BRL));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payment.getUpdatedAt()).isEqualTo(approvedAt);
    }
}
