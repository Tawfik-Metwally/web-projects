package io.github.tawfikmetwally.payments.service;

import java.util.UUID;

public record CreateRefundCommand(
        String merchantId,
        String idempotencyKey,
        UUID paymentId,
        String reason) {
}
