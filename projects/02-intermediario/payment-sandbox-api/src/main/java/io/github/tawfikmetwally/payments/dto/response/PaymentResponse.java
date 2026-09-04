package io.github.tawfikmetwally.payments.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;

public record PaymentResponse(
        UUID id,
        long amount,
        String currency,
        PaymentStatus status,
        String merchantReference,
        Instant createdAt,
        Instant updatedAt) {
}
