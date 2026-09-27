package io.github.tawfikmetwally.payments.dto.response;

import io.github.tawfikmetwally.payments.enums.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Completed full refund of a simulated payment.")
public record RefundResponse(
        @Schema(format = "uuid", example = "8f2db813-9a44-4fb7-8dcf-92976228e2b2")
        UUID id,

        @Schema(format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID paymentId,

        @Schema(description = "Refunded amount in minor units.", example = "10000")
        long amount,

        @Schema(example = "COMPLETED") RefundStatus status,
        @Schema(example = "CUSTOMER_REQUEST") String reason,

        @Schema(format = "date-time", example = "2026-09-18T18:35:00Z")
        Instant createdAt) {}
