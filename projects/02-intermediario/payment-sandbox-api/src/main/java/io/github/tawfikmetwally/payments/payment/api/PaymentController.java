package io.github.tawfikmetwally.payments.payment.api;

import java.net.URI;
import java.security.Principal;
import java.util.Currency;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.tawfikmetwally.payments.payment.application.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.payment.application.CreatePaymentResult;
import io.github.tawfikmetwally.payments.payment.application.CreatePaymentService;
import io.github.tawfikmetwally.payments.payment.domain.Payment;
import io.github.tawfikmetwally.payments.payment.domain.UnsupportedPaymentMethodTokenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final CreatePaymentService createPaymentService;

    public PaymentController(CreatePaymentService createPaymentService) {
        this.createPaymentService = createPaymentService;
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
}
