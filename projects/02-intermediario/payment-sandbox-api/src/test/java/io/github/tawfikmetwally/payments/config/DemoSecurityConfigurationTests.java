package io.github.tawfikmetwally.payments.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.controller.PaymentController;
import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;

@ActiveProfiles("demo-no-auth")
@Import(DemoSecurityConfiguration.class)
@WebMvcTest(PaymentController.class)
class DemoSecurityConfigurationTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_HEADER = "X-Demo-Merchant-Id";
    private static final String MERCHANT_ID = "merchant-a";
    private static final String IDEMPOTENCY_KEY = "idem-demo-123";
    private static final UUID PAYMENT_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePaymentService createPaymentService;

    @Test
    void rejectsMissingDemoMerchantHeaderBeforeCallingService() throws Exception {
        mockMvc.perform(validRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsBlankDemoMerchantHeaderBeforeCallingService() throws Exception {
        mockMvc.perform(validRequest().header(MERCHANT_HEADER, "   "))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsDemoMerchantHeaderLongerThanDatabaseLimitBeforeCallingService()
            throws Exception {
        mockMvc.perform(validRequest().header(MERCHANT_HEADER, "m".repeat(101)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void createsPrincipalFromValidDemoMerchantHeader() throws Exception {
        CreatePaymentCommand expectedCommand = new CreatePaymentCommand(
                MERCHANT_ID,
                IDEMPOTENCY_KEY,
                10_000,
                BRL,
                "ORDER-DEMO-123",
                "tok_approved");
        Payment payment = Payment.restore(
                PAYMENT_ID,
                MERCHANT_ID,
                "ORDER-DEMO-123",
                new Money(10_000, BRL),
                PaymentStatus.APPROVED,
                NOW,
                NOW);
        when(createPaymentService.create(expectedCommand))
                .thenReturn(new CreatePaymentResult(payment, false));

        mockMvc.perform(validRequest().header(MERCHANT_HEADER, MERCHANT_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ENDPOINT + "/" + PAYMENT_ID));

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .content("""
                        {
                          "amount": 10000,
                          "currency": "BRL",
                          "merchantReference": "ORDER-DEMO-123",
                          "paymentMethodToken": "tok_approved"
                        }
                        """);
    }
}
