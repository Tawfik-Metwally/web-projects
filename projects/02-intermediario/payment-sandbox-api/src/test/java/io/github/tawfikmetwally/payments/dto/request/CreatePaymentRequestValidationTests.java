package io.github.tawfikmetwally.payments.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreatePaymentRequestValidationTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsAValidRequest() {
        assertThat(validate(request(10_000L, "BRL", "ORDER-123", "tok_approved")))
                .isEmpty();
    }

    @Test
    void rejectsANullAmount() {
        assertViolationFor(request(null, "BRL", "ORDER-123", "tok_approved"), "amount");
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void rejectsANonPositiveAmount(long amount) {
        assertViolationFor(request(amount, "BRL", "ORDER-123", "tok_approved"), "amount");
    }

    @Test
    void rejectsABlankMerchantReference() {
        assertViolationFor(request(10_000L, "BRL", " ", "tok_approved"), "merchantReference");
    }

    @Test
    void rejectsAMerchantReferenceLongerThanOneHundredCharacters() {
        assertViolationFor(request(10_000L, "BRL", "A".repeat(101), "tok_approved"), "merchantReference");
    }

    @ParameterizedTest
    @ValueSource(strings = { " ", "brl", "BR", "BRLL" })
    void rejectsAnInvalidCurrencyFormat(String currency) {
        assertViolationFor(request(10_000L, currency, "ORDER-123", "tok_approved"), "currency");
    }

    @Test
    void rejectsABlankPaymentMethodToken() {
        assertViolationFor(request(10_000L, "BRL", "ORDER-123", " "), "paymentMethodToken");
    }

    @Test
    void rejectsAPaymentMethodTokenLongerThanOneHundredCharacters() {
        assertViolationFor(request(10_000L, "BRL", "ORDER-123", "t".repeat(101)), "paymentMethodToken");
    }

    private static CreatePaymentRequest request(
            Long amount,
            String currency,
            String merchantReference,
            String paymentMethodToken) {
        return new CreatePaymentRequest(amount, currency, merchantReference, paymentMethodToken);
    }

    private static Set<ConstraintViolation<CreatePaymentRequest>> validate(CreatePaymentRequest request) {
        return validator.validate(request);
    }

    private static void assertViolationFor(CreatePaymentRequest request, String propertyName) {
        boolean hasExpectedViolation = validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(propertyName));

        assertThat(hasExpectedViolation).isTrue();
    }
}
