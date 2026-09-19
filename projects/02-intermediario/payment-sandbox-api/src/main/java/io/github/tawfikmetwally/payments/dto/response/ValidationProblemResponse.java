package io.github.tawfikmetwally.payments.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ValidationProblemDetails",
        description = "Problem Details response with safe field-level validation errors.")
public record ValidationProblemResponse(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String title,
        @Schema(example = "Request validation failed.") String detail,
        @Schema(example = "/api/v1/payments") String instance,
        @Schema(
                example = "6dc06e1e-48c9-4e79-a572-e371346fa33f",
                format = "uuid")
        String traceId,
        @ArraySchema(schema = @Schema(implementation = FieldViolationResponse.class))
        List<FieldViolationResponse> errors) {
}
