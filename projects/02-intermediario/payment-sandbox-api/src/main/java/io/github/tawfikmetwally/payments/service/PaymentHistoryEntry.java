package io.github.tawfikmetwally.payments.service;

import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentHistoryEntry(
        UUID id, PaymentEventType eventType, PaymentStatus fromStatus, PaymentStatus toStatus, Instant occurredAt) {}
