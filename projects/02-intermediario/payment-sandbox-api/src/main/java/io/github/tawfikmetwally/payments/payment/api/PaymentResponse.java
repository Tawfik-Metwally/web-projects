package io.github.tawfikmetwally.payments.payment.api;

import java.time.Instant;
import java.util.UUID;

import io.github.tawfikmetwally.payments.payment.domain.PaymentStatus;

public record PaymentResponse(
        UUID id,
        long amount,
        String currency,
        PaymentStatus status,
        String merchantReference,
        Instant createdAt,
        Instant updatedAt) {
}
