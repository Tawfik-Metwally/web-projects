package io.github.tawfikmetwally.payments.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;

@Schema(description = "Immutable payment lifecycle event.")
public record PaymentEventResponse(
        @Schema(format = "uuid", example = "e9b0b69a-2c94-45bc-a44b-1aa4ed0abec8")
        UUID id,
        @Schema(example = "PAYMENT_APPROVED")
        PaymentEventType eventType,
        @Schema(description = "Previous status; absent for the creation event.",
                example = "PENDING", nullable = true)
        PaymentStatus fromStatus,
        @Schema(example = "APPROVED")
        PaymentStatus toStatus,
        @Schema(format = "date-time", example = "2026-09-18T18:30:00Z")
        Instant occurredAt) {
}
