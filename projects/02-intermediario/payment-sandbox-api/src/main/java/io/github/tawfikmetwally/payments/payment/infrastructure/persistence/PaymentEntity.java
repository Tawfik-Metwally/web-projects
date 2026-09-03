package io.github.tawfikmetwally.payments.payment.infrastructure.persistence;

import io.github.tawfikmetwally.payments.payment.domain.Money;
import io.github.tawfikmetwally.payments.payment.domain.Payment;
import io.github.tawfikmetwally.payments.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "merchant_reference", nullable = false, length = 100)
    private String merchantReference;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            UUID id,
            String merchantId,
            String merchantReference,
            long amountMinor,
            String currency,
            PaymentStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.merchantReference = merchantReference;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentEntity fromDomain(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");

        return new PaymentEntity(
                payment.getId(),
                payment.getMerchantId(),
                payment.getMerchantReference(),
                payment.getMoney().amountMinor(),
                payment.getMoney().currency().getCurrencyCode(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

    public Payment toDomain() {
        Money money = new Money(amountMinor, Currency.getInstance(currency));

        return Payment.restore(
                id,
                merchantId,
                merchantReference,
                money,
                status,
                createdAt,
                updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMerchantReference() {
        return merchantReference;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
