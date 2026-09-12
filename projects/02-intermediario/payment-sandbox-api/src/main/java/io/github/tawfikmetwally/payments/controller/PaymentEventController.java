package io.github.tawfikmetwally.payments.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.tawfikmetwally.payments.dto.response.PaymentEventResponse;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.service.GetPaymentHistoryService;
import io.github.tawfikmetwally.payments.service.PaymentHistoryEntry;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/events")
public class PaymentEventController {

    private final GetPaymentHistoryService getPaymentHistoryService;

    public PaymentEventController(
            GetPaymentHistoryService getPaymentHistoryService) {
        this.getPaymentHistoryService = getPaymentHistoryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PaymentEventResponse>> getHistory(
            @PathVariable UUID paymentId,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        List<PaymentEventResponse> response = getPaymentHistoryService
                .getHistory(paymentId, principal.getName())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private PaymentEventResponse toResponse(PaymentHistoryEntry event) {
        return new PaymentEventResponse(
                event.id(),
                event.eventType(),
                event.fromStatus(),
                event.toStatus(),
                event.occurredAt());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Void> handlePaymentNotFound() {
        return ResponseEntity.notFound().build();
    }
}
