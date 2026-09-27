package io.github.tawfikmetwally.payments.dto.response;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Merchant-owned simulated payment.")
public record PaymentResponse(
        @Schema(format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Amount in minor units.", example = "10000")
        long amount,

        @Schema(example = "BRL") String currency,
        @Schema(example = "APPROVED") PaymentStatus status,
        @Schema(example = "ORDER-2026-001") String merchantReference,

        @Schema(format = "date-time", example = "2026-09-18T18:30:00Z")
        Instant createdAt,

        @Schema(format = "date-time", example = "2026-09-18T18:30:00Z")
        Instant updatedAt) {}
