package io.github.tawfikmetwally.payments.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateRefundRequestValidationTests {

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
    void acceptsAValidReasonWithoutAnAmount() {
        assertThat(validate(new CreateRefundRequest("CUSTOMER_REQUEST")))
                .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankReason(String reason) {
        assertViolationFor(new CreateRefundRequest(reason), "reason");
    }

    @Test
    void rejectsReasonLongerThanDatabaseLimit() {
        assertViolationFor(new CreateRefundRequest("r".repeat(256)), "reason");
    }

    private static Set<ConstraintViolation<CreateRefundRequest>> validate(
            CreateRefundRequest request) {
        return validator.validate(request);
    }

    private static void assertViolationFor(
            CreateRefundRequest request,
            String propertyName) {
        boolean hasExpectedViolation = validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath()
                        .toString()
                        .equals(propertyName));
        assertThat(hasExpectedViolation).isTrue();
    }
}
