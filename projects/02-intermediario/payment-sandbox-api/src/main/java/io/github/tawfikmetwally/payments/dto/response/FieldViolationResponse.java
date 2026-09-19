package io.github.tawfikmetwally.payments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One rejected request field without its submitted value.")
public record FieldViolationResponse(
        @Schema(example = "amount") String field,
        @Schema(example = "Must be greater than zero.") String message) {
}
