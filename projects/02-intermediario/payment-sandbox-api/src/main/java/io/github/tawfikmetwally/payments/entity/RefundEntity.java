package io.github.tawfikmetwally.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.enums.RefundStatus;

@Entity
@Table(name = "refunds")
public class RefundEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private PaymentEntity payment;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefundEntity() {
    }

    public RefundEntity(
            UUID id,
            PaymentEntity payment,
            long amountMinor,
            RefundStatus status,
            String reason,
            Instant createdAt) {
        this.id = id;
        this.payment = payment;
        this.amountMinor = amountMinor;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static RefundEntity fromDomain(
            Refund refund,
            PaymentEntity payment) {
        Objects.requireNonNull(refund, "refund must not be null");
        Objects.requireNonNull(payment, "payment must not be null");
        if (!refund.getPaymentId().equals(payment.getId())) {
            throw new IllegalArgumentException(
                    "refund paymentId must match payment entity id");
        }

        return new RefundEntity(
                refund.getId(),
                payment,
                refund.getMoney().amountMinor(),
                refund.getStatus(),
                refund.getReason(),
                refund.getCreatedAt());
    }

    public Refund toDomain() {
        Money money = new Money(
                amountMinor,
                Currency.getInstance(payment.getCurrency()));
        return Refund.restore(
                id,
                payment.getId(),
                money,
                status,
                reason,
                createdAt);
    }

    public UUID getId() {
        return id;
    }

    public PaymentEntity getPayment() {
        return payment;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
