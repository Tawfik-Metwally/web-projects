package io.github.tawfikmetwally.payments.payment.application;

import io.github.tawfikmetwally.payments.payment.domain.Payment;

public record CreatePaymentResult(Payment payment, boolean replayed) {
}
