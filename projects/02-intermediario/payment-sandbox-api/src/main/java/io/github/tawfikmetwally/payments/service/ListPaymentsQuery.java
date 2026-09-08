package io.github.tawfikmetwally.payments.service;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;

public record ListPaymentsQuery(
        String merchantId,
        int page,
        int size,
        PaymentStatus status) {
}
