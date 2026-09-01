package io.github.tawfikmetwally.payments.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {

    private static final int MAX_MERCHANT_ID_LENGTH = 100;
    private static final int MAX_MERCHANT_REFERENCE_LENGTH = 100;

    private final UUID id;
    private final String merchantId;
    private final String merchantReference;
    private final Money money;
    private PaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(
            UUID id,
            String merchantId,
            String merchantReference,
            Money money,
            PaymentStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.merchantId = requireText(
                merchantId,
                "merchantId",
                MAX_MERCHANT_ID_LENGTH);
        this.merchantReference = requireText(
                merchantReference,
                "merchantReference",
                MAX_MERCHANT_REFERENCE_LENGTH);
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static Payment create(
            UUID id,
            String merchantId,
            String merchantReference,
            Money money,
            Instant createdAt) {
        return new Payment(
                id,
                merchantId,
                merchantReference,
                money,
                PaymentStatus.PENDING,
                createdAt,
                createdAt);
    }

    public static Payment restore(
            UUID id,
            String merchantId,
            String merchantReference,
            Money money,
            PaymentStatus status,
            Instant createdAt,
            Instant updatedAt) {
        return new Payment(
                id,
                merchantId,
                merchantReference,
                money,
                status,
                createdAt,
                updatedAt);
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
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

    public Money getMoney() {
        return money;
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
