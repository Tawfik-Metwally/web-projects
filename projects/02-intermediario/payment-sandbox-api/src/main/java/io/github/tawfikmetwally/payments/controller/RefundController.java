package io.github.tawfikmetwally.payments.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.dto.request.CreateRefundRequest;
import io.github.tawfikmetwally.payments.dto.response.RefundResponse;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.PaymentNotRefundableException;
import io.github.tawfikmetwally.payments.service.CreateRefundCommand;
import io.github.tawfikmetwally.payments.service.CreateRefundResult;
import io.github.tawfikmetwally.payments.service.CreateRefundService;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/refunds")
public class RefundController {

    private final CreateRefundService createRefundService;

    public RefundController(CreateRefundService createRefundService) {
        this.createRefundService = createRefundService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RefundResponse> create(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255)
                    String idempotencyKey,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CreateRefundCommand command = new CreateRefundCommand(
                principal.getName(),
                idempotencyKey,
                paymentId,
                request.reason());
        CreateRefundResult result = createRefundService.create(command);
        RefundResponse response = toResponse(result.refund());

        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotency-Replayed", "true")
                    .body(response);
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

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Void> handleIdempotencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Void> handlePaymentNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(PaymentNotRefundableException.class)
    public ResponseEntity<Void> handlePaymentNotRefundable() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
