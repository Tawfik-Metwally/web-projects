package io.github.tawfikmetwally.payments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requests a full refund; the API derives the amount from the payment.")
public record CreateRefundRequest(
        @Schema(description = "Business reason for the full refund.", example = "CUSTOMER_REQUEST", maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String reason) {}
