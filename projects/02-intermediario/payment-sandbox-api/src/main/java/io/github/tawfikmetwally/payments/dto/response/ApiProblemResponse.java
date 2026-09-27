package io.github.tawfikmetwally.payments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProblemDetails", description = "Safe RFC 9457-style error response.")
public record ApiProblemResponse(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String title,

        @Schema(example = "Request content or parameters are invalid.")
        String detail,

        @Schema(example = "/api/v1/payments") String instance,

        @Schema(example = "6dc06e1e-48c9-4e79-a572-e371346fa33f", format = "uuid")
        String traceId) {}
