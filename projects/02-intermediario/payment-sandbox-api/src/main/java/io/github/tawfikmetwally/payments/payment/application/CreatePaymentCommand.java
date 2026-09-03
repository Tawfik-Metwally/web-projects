package io.github.tawfikmetwally.payments.payment.application;

import java.util.Currency;

public record CreatePaymentCommand(
        String merchantId,
        String idempotencyKey,
        long amountMinor,
        Currency currency,
        String merchantReference,
        String paymentMethodToken) {
}
