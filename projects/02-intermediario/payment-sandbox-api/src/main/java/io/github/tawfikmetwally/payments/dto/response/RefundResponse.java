package io.github.tawfikmetwally.payments.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.github.tawfikmetwally.payments.enums.RefundStatus;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        long amount,
        RefundStatus status,
        String reason,
        Instant createdAt) {
}
