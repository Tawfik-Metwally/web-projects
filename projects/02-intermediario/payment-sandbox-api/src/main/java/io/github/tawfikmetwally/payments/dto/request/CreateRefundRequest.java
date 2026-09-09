package io.github.tawfikmetwally.payments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @NotBlank @Size(max = 255) String reason) {
}
