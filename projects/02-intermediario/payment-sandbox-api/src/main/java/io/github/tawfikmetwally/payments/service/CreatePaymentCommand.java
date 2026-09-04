package io.github.tawfikmetwally.payments.service;

import java.util.Currency;

public record CreatePaymentCommand(
        String merchantId,
        String idempotencyKey,
        long amountMinor,
        Currency currency,
        String merchantReference,
        String paymentMethodToken) {
}
