package io.github.tawfikmetwally.payments.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One page of merchant-owned payments.")
public record PaymentPageResponse(
        @ArraySchema(schema = @Schema(implementation = PaymentResponse.class))
        List<PaymentResponse> content,
        @Schema(example = "0", minimum = "0")
        int page,
        @Schema(example = "20", minimum = "1", maximum = "100")
        int size,
        @Schema(example = "1", minimum = "0")
        long totalElements,
        @Schema(example = "1", minimum = "0")
        int totalPages) {

    public PaymentPageResponse {
        content = List.copyOf(content);
    }
}
