package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class JwtMerchantIsolationIntegrationTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_A = "merchant-a-client";
    private static final String MERCHANT_B = "merchant-b-client";
    private static final JwtSigningTestSupport SIGNING = new JwtSigningTestSupport();

    @Autowired private MockMvc mockMvc;
    @Autowired private PaymentJpaRepository payments;
    @Autowired private RefundJpaRepository refunds;
    @Autowired private PaymentEventJpaRepository events;
    @Autowired private IdempotencyRecordJpaRepository idempotency;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        SIGNING.registerProperties(registry);
    }

    @AfterAll
    static void stopSigningServer() {
        SIGNING.close();
    }

    @BeforeEach
    void clearTemporaryDatabase() {
        idempotency.deleteAllInBatch();
        refunds.deleteAllInBatch();
        events.deleteAllInBatch();
        payments.deleteAllInBatch();
    }

    @Test
    void persistsAuthorizedPartyInsteadOfSubjectOrSpoofedMerchantInputs() throws Exception {
        MvcResult result = mockMvc.perform(createRequest(MERCHANT_A, "create-a")
                        .header("X-Demo-Merchant-Id", MERCHANT_B)
                        .header("X-Merchant-Id", MERCHANT_B)
                        .queryParam("merchantId", MERCHANT_B))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = paymentId(result);
        assertThat(payments.findByIdAndMerchantId(paymentId, MERCHANT_A)).isPresent();
        assertThat(payments.findByIdAndMerchantId(paymentId, MERCHANT_B)).isEmpty();
        assertThat(payments.findByIdAndMerchantId(paymentId, "internal-service-account")).isEmpty();
        assertCounts(1, 0, 2, 1);
    }

    @ParameterizedTest
    @CsvSource({
            "merchant-a-client, merchant-b-client",
            "merchant-b-client, merchant-a-client"
    })
    void allowsOwnerAndHidesPaymentAndHistoryFromOtherMerchant(
            String owner, String outsider) throws Exception {
        UUID paymentId = createPayment(owner, "create-owner");

        mockMvc.perform(bearer(get(ENDPOINT + "/" + paymentId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
        mockMvc.perform(bearer(get(ENDPOINT + "/" + paymentId + "/events"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(bearer(get(ENDPOINT + "/" + paymentId), outsider)
                        .header("X-Merchant-Id", owner)
                        .queryParam("merchantId", owner))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(bearer(get(ENDPOINT + "/" + paymentId + "/events"), outsider))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(bearer(get(ENDPOINT + "/" + UUID.randomUUID()), outsider))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        assertCounts(1, 0, 2, 1);
    }

    @Test
    void isolatesListingsAndPaginationDespiteSameSubjectAndSpoofedFilter() throws Exception {
        UUID firstA = createPayment(MERCHANT_A, "a-1");
        UUID onlyB = createPayment(MERCHANT_B, "b-1");
        UUID secondA = createPayment(MERCHANT_A, "a-2");

        mockMvc.perform(bearer(get(ENDPOINT), MERCHANT_A)
                        .queryParam("merchantId", MERCHANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                firstA.toString(), secondA.toString())));
        mockMvc.perform(bearer(get(ENDPOINT), MERCHANT_B)
                        .queryParam("merchantId", MERCHANT_A)
                        .queryParam("status", "APPROVED")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value(onlyB.toString()));
    }

    @ParameterizedTest
    @CsvSource({
            "merchant-a-client, merchant-b-client",
            "merchant-b-client, merchant-a-client"
    })
    void deniesCrossMerchantRefundWithoutChangesAndAllowsOwner(
            String owner, String outsider) throws Exception {
        UUID paymentId = createPayment(owner, "payment-for-refund");

        mockMvc.perform(refundRequest(outsider, paymentId, "refund-shared")
                        .header("X-Demo-Merchant-Id", owner))
                .andExpect(status().isNotFound());
        assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.APPROVED);
        assertCounts(1, 0, 2, 1);

        mockMvc.perform(refundRequest(owner, paymentId, "refund-shared"))
                .andExpect(status().isCreated());
        assertCounts(1, 1, 3, 2);

        // Knowing the owner's successful key must not reveal its replay.
        mockMvc.perform(refundRequest(outsider, paymentId, "refund-shared"))
                .andExpect(status().isNotFound());
        assertCounts(1, 1, 3, 2);
    }

    @Test
    void isolatesCreationAndRefundIdempotencyBetweenSignedMerchantIdentities() throws Exception {
        UUID paymentA = createPayment(MERCHANT_A, "shared-creation-key");
        UUID paymentB = createPayment(MERCHANT_B, "shared-creation-key");
        assertThat(paymentA).isNotEqualTo(paymentB);

        for (String merchant : List.of(MERCHANT_A, MERCHANT_B)) {
            UUID expected = merchant.equals(MERCHANT_A) ? paymentA : paymentB;
            mockMvc.perform(createRequest(merchant, "shared-creation-key"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(jsonPath("$.id").value(expected.toString()));
            mockMvc.perform(refundRequest(merchant, expected, "shared-refund-key"))
                    .andExpect(status().isCreated());
            mockMvc.perform(refundRequest(merchant, expected, "shared-refund-key"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(jsonPath("$.paymentId").value(expected.toString()));
        }
        assertCounts(2, 2, 6, 4);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void rejectsInvalidSignedIdentityWithoutPersistingAnything(Object identity) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNING.token(identity))
                        .header("Idempotency-Key", "invalid-identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody()))
                .andExpect(status().isUnauthorized());
        assertCounts(0, 0, 0, 0);
    }

    static Stream<Object> invalidIdentities() {
        return Stream.of(null, "", " ", " merchant-a-client",
                "merchant-a-client ", "x".repeat(101), 42);
    }

    @Test
    void rejectsForeignSignatureBeforePersistence() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + SIGNING.tokenWithForeignSignature(MERCHANT_A))
                        .header("Idempotency-Key", "foreign-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody()))
                .andExpect(status().isUnauthorized());
        assertCounts(0, 0, 0, 0);
    }

    private UUID createPayment(String merchant, String key) throws Exception {
        return paymentId(mockMvc.perform(createRequest(merchant, key))
                .andExpect(status().isCreated()).andReturn());
    }

    private UUID paymentId(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private MockHttpServletRequestBuilder createRequest(String merchant, String key)
            throws Exception {
        return bearer(post(ENDPOINT), merchant)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentBody());
    }

    private MockHttpServletRequestBuilder refundRequest(String merchant, UUID paymentId, String key)
            throws Exception {
        return bearer(post(ENDPOINT + "/" + paymentId + "/refunds"), merchant)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"CUSTOMER_REQUEST\"}");
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String merchant)
            throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNING.token(merchant));
    }

    private String paymentBody() {
        return """
                {
                  "amount": 10000,
                  "currency": "BRL",
                  "merchantReference": "ORDER-JWT-ISOLATION",
                  "paymentMethodToken": "tok_approved"
                }
                """;
    }

    private void assertCounts(long paymentCount, long refundCount, long eventCount, long recordCount) {
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(refunds.count()).isEqualTo(refundCount);
        assertThat(events.count()).isEqualTo(eventCount);
        assertThat(idempotency.count()).isEqualTo(recordCount);
    }
}
