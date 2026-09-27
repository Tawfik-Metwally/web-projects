package io.github.tawfikmetwally.payments.controller;

import io.github.tawfikmetwally.payments.dto.response.ApiProblemResponse;
import io.github.tawfikmetwally.payments.dto.response.PaymentEventResponse;
import io.github.tawfikmetwally.payments.service.GetPaymentHistoryService;
import io.github.tawfikmetwally.payments.service.PaymentHistoryEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/events")
@Tag(name = "Payment history", description = "Read immutable payment lifecycle events.")
public class PaymentEventController {

    private final GetPaymentHistoryService getPaymentHistoryService;

    public PaymentEventController(GetPaymentHistoryService getPaymentHistoryService) {
        this.getPaymentHistoryService = getPaymentHistoryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get payment history",
            description = "Required scope: `payments:read`. Events are ordered by occurrence.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Payment events returned.",
                content =
                        @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentEventResponse.class)))),
        @ApiResponse(
                responseCode = "400",
                description = "Payment ID is not a UUID.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Token lacks payments:read.",
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
                responseCode = "500",
                description = "Unexpected internal failure.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public ResponseEntity<List<PaymentEventResponse>> getHistory(
            @Parameter(description = "Payment identifier.", required = true) @PathVariable UUID paymentId,
            @Parameter(hidden = true) Principal principal) {
        List<PaymentEventResponse> response =
                getPaymentHistoryService.getHistory(paymentId, principal.getName()).stream()
                        .map(this::toResponse)
                        .toList();
        return ResponseEntity.ok(response);
    }

    private PaymentEventResponse toResponse(PaymentHistoryEntry event) {
        return new PaymentEventResponse(
                event.id(), event.eventType(), event.fromStatus(), event.toStatus(), event.occurredAt());
    }
}
