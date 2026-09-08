package io.github.tawfikmetwally.payments.controller;

import java.net.URI;
import java.security.Principal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.dto.request.CreatePaymentRequest;
import io.github.tawfikmetwally.payments.dto.response.PaymentPageResponse;
import io.github.tawfikmetwally.payments.dto.response.PaymentResponse;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.UnsupportedPaymentMethodTokenException;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;
import io.github.tawfikmetwally.payments.service.GetPaymentService;
import io.github.tawfikmetwally.payments.service.ListPaymentsQuery;
import io.github.tawfikmetwally.payments.service.ListPaymentsResult;
import io.github.tawfikmetwally.payments.service.ListPaymentsService;

@RestController
@RequestMapping("/api/v1/payments")
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
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
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
    public ResponseEntity<PaymentResponse> getById(
            @PathVariable UUID paymentId,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Payment payment = getPaymentService.getById(paymentId, principal.getName());
        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentPageResponse> list(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "status", required = false) PaymentStatus status,
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

    @ExceptionHandler(UnsupportedPaymentMethodTokenException.class)
    public ResponseEntity<Void> handleUnsupportedPaymentMethodToken() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Void> handleIdempotencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Void> handlePaymentNotFound() {
        return ResponseEntity.notFound().build();
    }
}
