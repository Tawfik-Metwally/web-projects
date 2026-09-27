package io.github.tawfikmetwally.payments.controller;

import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.dto.request.CreateRefundRequest;
import io.github.tawfikmetwally.payments.dto.response.ApiProblemResponse;
import io.github.tawfikmetwally.payments.dto.response.RefundResponse;
import io.github.tawfikmetwally.payments.dto.response.ValidationProblemResponse;
import io.github.tawfikmetwally.payments.service.CreateRefundCommand;
import io.github.tawfikmetwally.payments.service.CreateRefundResult;
import io.github.tawfikmetwally.payments.service.CreateRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/refunds")
@Tag(name = "Refunds", description = "Create idempotent full refunds.")
public class RefundController {

    private final CreateRefundService createRefundService;

    public RefundController(CreateRefundService createRefundService) {
        this.createRefundService = createRefundService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a full refund",
            description = "Required scope: `refunds:create`. The API derives the amount "
                    + "from the merchant-owned payment.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Refund created.",
                content = @Content(schema = @Schema(implementation = RefundResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "Identical idempotent replay.",
                headers = @Header(name = "Idempotency-Replayed", description = "Present with value true for a replay."),
                content = @Content(schema = @Schema(implementation = RefundResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Malformed input, validation failure, or invalid payment ID.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(oneOf = {ApiProblemResponse.class, ValidationProblemResponse.class}))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Token lacks refunds:create.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Payment absent or owned by another merchant.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency conflict or payment not refundable.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal failure.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public ResponseEntity<RefundResponse> create(
            @Parameter(description = "Payment identifier.", required = true) @PathVariable UUID paymentId,
            @Valid @RequestBody CreateRefundRequest request,
            @Parameter(
                            description = "Merchant-scoped key used to replay the same request safely.",
                            required = true,
                            example = "refund-create-001")
                    @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Size(max = 255)
                    String idempotencyKey,
            @Parameter(hidden = true) Principal principal) {
        CreateRefundCommand command =
                new CreateRefundCommand(principal.getName(), idempotencyKey, paymentId, request.reason());
        CreateRefundResult result = createRefundService.create(command);
        RefundResponse response = toResponse(result.refund());

        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotency-Replayed", "true").body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getMoney().amountMinor(),
                refund.getStatus(),
                refund.getReason(),
                refund.getCreatedAt());
    }
}
