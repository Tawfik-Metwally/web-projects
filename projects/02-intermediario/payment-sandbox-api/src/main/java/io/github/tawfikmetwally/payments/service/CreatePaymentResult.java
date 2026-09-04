package io.github.tawfikmetwally.payments.service;

import io.github.tawfikmetwally.payments.domain.Payment;

public record CreatePaymentResult(Payment payment, boolean replayed) {
}
