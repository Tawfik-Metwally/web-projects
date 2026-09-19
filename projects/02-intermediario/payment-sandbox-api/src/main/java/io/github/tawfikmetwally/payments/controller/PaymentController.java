package io.github.tawfikmetwally.payments.controller;

import java.net.URI;
import java.security.Principal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.dto.request.CreatePaymentRequest;
import io.github.tawfikmetwally.payments.dto.response.ApiProblemResponse;
import io.github.tawfikmetwally.payments.dto.response.PaymentPageResponse;
import io.github.tawfikmetwally.payments.dto.response.PaymentResponse;
import io.github.tawfikmetwally.payments.dto.response.ValidationProblemResponse;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;
import io.github.tawfikmetwally.payments.service.GetPaymentService;
import io.github.tawfikmetwally.payments.service.ListPaymentsQuery;
import io.github.tawfikmetwally.payments.service.ListPaymentsResult;
import io.github.tawfikmetwally.payments.service.ListPaymentsService;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Create and read merchant-isolated payments.")
public class PaymentController {

    private final CreatePaymentService createPaymentService;
    private final GetPaymentService getPaymentService;
    private final ListPaymentsService listPaymentsService;

    public PaymentController(
            CreatePaymentService createPaymentService,
            GetPaymentService getPaymentService,
            ListPaymentsService listPaymentsService) {
        this.createPaymentService = createPaymentService;
        this.getPaymentService = getPaymentService;
        this.listPaymentsService = listPaymentsService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a payment",
            description = "Required scope: `payments:create`. A new idempotency key "
                    + "returns 201; an identical replay returns 200.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Payment created.",
                headers = @Header(name = "Location", description = "Created payment URI."),
                content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "Identical idempotent replay.",
                headers = @Header(
                        name = "Idempotency-Replayed",
                        description = "Present with value true for a replay."),
                content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Malformed input, validation failure, unsupported currency, "
                        + "or unsupported sandbox token.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(oneOf = {
                            ApiProblemResponse.class,
                            ValidationProblemResponse.class
                        }))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "403", description = "Token lacks payments:create.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency key conflict.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected internal failure.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @Parameter(
                    description = "Merchant-scoped key used to replay the same request safely.",
                    required = true,
                    example = "payment-create-001")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Parameter(hidden = true)
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CreatePaymentCommand command = new CreatePaymentCommand(
                principal.getName(),
                idempotencyKey,
                request.amount(),
                parseCurrency(request.currency()),
                request.merchantReference(),
                request.paymentMethodToken());

        CreatePaymentResult result = createPaymentService.create(command);
        PaymentResponse response = toResponse(result.payment());

        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotency-Replayed", "true")
                    .body(response);
        }

        URI location = URI.create("/api/v1/payments/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping(value = "/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a payment",
            description = "Required scope: `payments:read`. A payment owned by another "
                    + "merchant is indistinguishable from a missing payment.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found.",
                content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payment ID is not a UUID.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "403", description = "Token lacks payments:read.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment absent or owned by another merchant.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected internal failure.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public ResponseEntity<PaymentResponse> getById(
            @Parameter(description = "Payment identifier.", required = true)
            @PathVariable UUID paymentId,
            @Parameter(hidden = true)
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Payment payment = getPaymentService.getById(paymentId, principal.getName());
        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List payments",
            description = "Required scope: `payments:read`. Results contain only payments "
                    + "owned by the authenticated merchant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page returned.",
                content = @Content(schema = @Schema(implementation = PaymentPageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid page, size, or status.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(oneOf = {
                            ApiProblemResponse.class,
                            ValidationProblemResponse.class
                        }))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "403", description = "Token lacks payments:read.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected internal failure.",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public ResponseEntity<PaymentPageResponse> list(
            @Parameter(description = "Zero-based page index.", example = "0")
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Items per page, from 1 to 100.", example = "20")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Optional exact payment status filter.")
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @Parameter(hidden = true)
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        ListPaymentsQuery query = new ListPaymentsQuery(
                principal.getName(),
                page,
                size,
                status);
        ListPaymentsResult result = listPaymentsService.list(query);
        List<PaymentResponse> content = result.payments().stream()
                .map(this::toResponse)
                .toList();

        PaymentPageResponse response = new PaymentPageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
        return ResponseEntity.ok(response);
    }

    private Currency parseCurrency(String currencyCode) {
        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency");
        }

        if (!"BRL".equals(currency.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only BRL is supported");
        }
        return currency;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMoney().amountMinor(),
                payment.getMoney().currency().getCurrencyCode(),
                payment.getStatus(),
                payment.getMerchantReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

}
