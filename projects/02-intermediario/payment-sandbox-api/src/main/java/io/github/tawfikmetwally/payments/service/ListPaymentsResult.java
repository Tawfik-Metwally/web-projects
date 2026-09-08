package io.github.tawfikmetwally.payments.service;

import java.util.List;

import io.github.tawfikmetwally.payments.domain.Payment;

public record ListPaymentsResult(
        List<Payment> payments,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ListPaymentsResult {
        payments = List.copyOf(payments);
    }
}
