package io.github.tawfikmetwally.payments.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.tawfikmetwally.payments.enums.RefundStatus;

public final class Refund {

    private static final int MAX_REASON_LENGTH = 255;

    private final UUID id;
    private final UUID paymentId;
    private final Money money;
    private final RefundStatus status;
    private final String reason;
    private final Instant createdAt;

    private Refund(
            UUID id,
            UUID paymentId,
            Money money,
            RefundStatus status,
            String reason,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.paymentId = Objects.requireNonNull(
                paymentId,
                "paymentId must not be null");
        this.money = Objects.requireNonNull(money, "money must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reason = requireReason(reason);
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null");
    }

    static Refund create(
            UUID id,
            UUID paymentId,
            Money money,
            String reason,
            Instant createdAt) {
        return new Refund(
                id,
                paymentId,
                money,
                RefundStatus.COMPLETED,
                reason,
                createdAt);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "reason must not exceed 255 characters");
        }
        return reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Money getMoney() {
        return money;
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
