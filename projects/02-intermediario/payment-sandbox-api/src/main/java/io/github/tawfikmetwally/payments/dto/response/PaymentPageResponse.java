package io.github.tawfikmetwally.payments.dto.response;

import java.util.List;

public record PaymentPageResponse(
        List<PaymentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PaymentPageResponse {
        content = List.copyOf(content);
    }
}
