package io.github.tawfikmetwally.payments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Creates a simulated payment. Never submit real payment data.")
public record CreatePaymentRequest(
        @Schema(
                description = "Amount in minor units; 10000 represents BRL 100.00.",
                example = "10000",
                minimum = "1")
        @NotNull @Positive Long amount,
        @Schema(description = "Supported ISO currency code.", example = "BRL",
                allowableValues = "BRL")
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Schema(description = "Merchant-controlled reference.", example = "ORDER-2026-001",
                maxLength = 100)
        @NotBlank @Size(max = 100) String merchantReference,
        @Schema(
                description = "Sandbox-only deterministic token.",
                example = "tok_approved",
                allowableValues = {"tok_approved", "tok_declined"},
                maxLength = 100)
        @NotBlank @Size(max = 100) String paymentMethodToken) {
}
