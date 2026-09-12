package io.github.tawfikmetwally.payments.service;

import java.time.Instant;
import java.util.UUID;

import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;

public record PaymentHistoryEntry(
        UUID id,
        PaymentEventType eventType,
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        Instant occurredAt) {
}
