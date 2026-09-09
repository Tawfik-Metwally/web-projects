package io.github.tawfikmetwally.payments.service;

import io.github.tawfikmetwally.payments.domain.Refund;

public record CreateRefundResult(Refund refund, boolean replayed) {
}
