package io.github.tawfikmetwally.payments.service;

import io.github.tawfikmetwally.payments.domain.Payment;
import java.util.List;

public record ListPaymentsResult(List<Payment> payments, int page, int size, long totalElements, int totalPages) {

    public ListPaymentsResult {
        payments = List.copyOf(payments);
    }
}
