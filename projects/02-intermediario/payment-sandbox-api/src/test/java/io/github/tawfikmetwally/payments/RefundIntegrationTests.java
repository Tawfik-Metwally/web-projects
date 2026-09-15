package io.github.tawfikmetwally.payments;

import static io.github.tawfikmetwally.payments.JwtTestAuthentication.merchantJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class RefundIntegrationTests {

    private static final String PAYMENTS_ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_A = "merchant-refund-a";
    private static final String MERCHANT_B = "merchant-refund-b";
    private static final Instant BASE_TIME = Instant.parse(
            "2026-09-12T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentEventJpaRepository paymentEventRepository;

    @Autowired
    private RefundJpaRepository refundRepository;

    @Autowired
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearTemporaryDatabase() {
        idempotencyRecordRepository.deleteAllInBatch();
        refundRepository.deleteAllInBatch();
        paymentEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
    }

    @Test
    void createsFullRefundEventAndQueryableHistory() throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-success");

        MvcResult refundResult = mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-success",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reason").value("CUSTOMER_REQUEST"))
                .andReturn();
        String refundId = JsonPath.read(
                refundResult.getResponse().getContentAsString(),
                "$.id");

        PaymentEntity payment = paymentRepository
                .findByIdAndMerchantId(paymentId, MERCHANT_A)
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refundRepository
                        .findByPayment_IdAndPayment_MerchantId(
                                paymentId,
                                MERCHANT_A))
                .isPresent()
                .get()
                .satisfies(refund -> {
                    assertThat(refund.getId().toString()).isEqualTo(refundId);
                    assertThat(refund.getAmountMinor()).isEqualTo(10_000);
                });
        assertThat(paymentEventRepository
                        .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                                paymentId,
                                MERCHANT_A))
                .extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder(
                        PaymentEventType.PAYMENT_CREATED,
                        PaymentEventType.PAYMENT_APPROVED,
                        PaymentEventType.PAYMENT_REFUNDED);
        assertThat(idempotencyRecordRepository
                        .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                                MERCHANT_A,
                                IdempotencyOperation.CREATE_REFUND,
                                "refund-key-success"))
                .isPresent();

        MvcResult historyResult = mockMvc.perform(get(historyEndpoint(paymentId))
                        .with(merchantJwt(MERCHANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].eventType")
                        .value("PAYMENT_REFUNDED"))
                .andExpect(jsonPath("$[2].fromStatus").value("APPROVED"))
                .andExpect(jsonPath("$[2].toStatus").value("REFUNDED"))
                .andReturn();
        List<String> eventTypes = JsonPath.read(
                historyResult.getResponse().getContentAsString(),
                "$[*].eventType");
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "PAYMENT_CREATED",
                "PAYMENT_APPROVED",
                "PAYMENT_REFUNDED");
        assertPersistedCounts(1, 1, 3, 2);
    }

    @Test
    void replaysIdenticalRefundWithoutDuplicatingPersistedData()
            throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-replay");
        MockHttpServletRequestBuilder request = refundRequest(
                paymentId,
                MERCHANT_A,
                "refund-key-replay",
                "CUSTOMER_REQUEST");
        MvcResult first = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        String createdRefundId = JsonPath.read(
                first.getResponse().getContentAsString(),
                "$.id");

        mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-replay",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(createdRefundId));

        assertPersistedCounts(1, 1, 3, 2);
    }

    @Test
    void rejectsChangedReasonWithSameKeyWithoutPartialData() throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-hash-conflict");
        mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-hash-conflict",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isCreated());

        mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-hash-conflict",
                        "DUPLICATE_CHARGE"))
                .andExpect(status().isConflict());

        assertPersistedCounts(1, 1, 3, 2);
    }

    @Test
    void rejectsNewKeyForAlreadyRefundedPaymentWithoutPartialData()
            throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-second-refund");
        mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-first",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isCreated());

        mockMvc.perform(refundRequest(
                        paymentId,
                        MERCHANT_A,
                        "refund-key-second",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isConflict());

        assertPersistedCounts(1, 1, 3, 2);
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentStatus.class,
            names = {"PENDING", "DECLINED"})
    void rejectsOwnPaymentInNonRefundableState(PaymentStatus status)
            throws Exception {
        PaymentEntity payment = savePayment(MERCHANT_A, status);

        mockMvc.perform(refundRequest(
                        payment.getId(),
                        MERCHANT_A,
                        "refund-key-invalid-state-" + status,
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isConflict());

        assertThat(paymentRepository.findById(payment.getId()))
                .isPresent()
                .get()
                .extracting(PaymentEntity::getStatus)
                .isEqualTo(status);
        assertPersistedCounts(1, 0, 0, 0);
    }

    @Test
    void returnsNotFoundForMissingPayment() throws Exception {
        mockMvc.perform(refundRequest(
                        UUID.randomUUID(),
                        MERCHANT_A,
                        "refund-key-missing",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isNotFound());

        assertPersistedCounts(0, 0, 0, 0);
    }

    @Test
    void hidesOtherMerchantPaymentDuringRefund() throws Exception {
        PaymentEntity payment = savePayment(MERCHANT_A, PaymentStatus.APPROVED);

        mockMvc.perform(refundRequest(
                        payment.getId(),
                        MERCHANT_B,
                        "refund-key-private",
                        "CUSTOMER_REQUEST"))
                .andExpect(status().isNotFound());

        assertThat(paymentRepository.findById(payment.getId()))
                .isPresent()
                .get()
                .extracting(PaymentEntity::getStatus)
                .isEqualTo(PaymentStatus.APPROVED);
        assertPersistedCounts(1, 0, 0, 0);
    }

    @Test
    void hidesOtherMerchantPaymentHistory() throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-private-history");

        mockMvc.perform(get(historyEndpoint(paymentId))
                        .with(merchantJwt(MERCHANT_B)))
                .andExpect(status().isNotFound());

        assertPersistedCounts(1, 0, 2, 1);
    }

    @Test
    void replaysConcurrentRequestWithSameKeyAfterRealRollback()
            throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-concurrent-replay");
        AtomicInteger lookupCount = synchronizeInitialPaymentLookups(paymentId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> refund = () -> mockMvc.perform(refundRequest(
                            paymentId,
                            MERCHANT_A,
                            "refund-key-concurrent-same",
                            "CUSTOMER_REQUEST"))
                    .andReturn();

            List<MvcResult> results = concurrentResults(executor, refund, refund);

            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 200);
            MvcResult created = resultWithStatus(results, 201);
            MvcResult replayed = resultWithStatus(results, 200);
            String createdRefundId = JsonPath.read(
                    created.getResponse().getContentAsString(),
                    "$.id");
            String replayedRefundId = JsonPath.read(
                    replayed.getResponse().getContentAsString(),
                    "$.id");
            assertThat(replayedRefundId).isEqualTo(createdRefundId);
            assertThat(replayed.getResponse().getHeader(
                    "Idempotency-Replayed")).isEqualTo("true");
            assertThat(lookupCount).hasValue(2);
            assertPersistedCounts(1, 1, 3, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsConcurrentRequestWithDifferentKeyAfterRealRollback()
            throws Exception {
        UUID paymentId = createApprovedPayment("payment-key-concurrent-conflict");
        AtomicInteger lookupCount = synchronizeInitialPaymentLookups(paymentId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> firstRefund = () -> mockMvc.perform(refundRequest(
                            paymentId,
                            MERCHANT_A,
                            "refund-key-concurrent-a",
                            "CUSTOMER_REQUEST"))
                    .andReturn();
            Callable<MvcResult> secondRefund = () -> mockMvc.perform(refundRequest(
                            paymentId,
                            MERCHANT_A,
                            "refund-key-concurrent-b",
                            "CUSTOMER_REQUEST"))
                    .andReturn();

            List<MvcResult> results = concurrentResults(
                    executor,
                    firstRefund,
                    secondRefund);

            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(lookupCount).hasValue(2);
            assertPersistedCounts(1, 1, 3, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createApprovedPayment(String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post(PAYMENTS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(merchantJwt(MERCHANT_A))
                        .header("Idempotency-Key", idempotencyKey)
                        .content("""
                                {
                                  "amount": 10000,
                                  "currency": "BRL",
                                  "merchantReference": "ORDER-REFUND-INTEGRATION",
                                  "paymentMethodToken": "tok_approved"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();
        String paymentId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id");
        return UUID.fromString(paymentId);
    }

    private MockHttpServletRequestBuilder refundRequest(
            UUID paymentId,
            String merchantId,
            String idempotencyKey,
            String reason) {
        return post(PAYMENTS_ENDPOINT + "/{paymentId}/refunds", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(merchantJwt(merchantId))
                .header("Idempotency-Key", idempotencyKey)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private String historyEndpoint(UUID paymentId) {
        return PAYMENTS_ENDPOINT + "/" + paymentId + "/events";
    }

    private PaymentEntity savePayment(
            String merchantId,
            PaymentStatus status) {
        return paymentRepository.saveAndFlush(new PaymentEntity(
                UUID.randomUUID(),
                merchantId,
                "ORDER-DIRECT-SETUP",
                10_000,
                "BRL",
                status,
                BASE_TIME,
                BASE_TIME));
    }

    private AtomicInteger synchronizeInitialPaymentLookups(UUID paymentId) {
        CyclicBarrier bothLookupsCompleted = new CyclicBarrier(2);
        AtomicInteger lookupCount = new AtomicInteger();

        doAnswer(invocation -> {
            Optional<PaymentEntity> result = findPayment(
                    invocation.getArgument(0),
                    invocation.getArgument(1));
            int currentLookup = lookupCount.incrementAndGet();
            if (currentLookup <= 2) {
                assertThat(result).isPresent()
                        .get()
                        .extracting(PaymentEntity::getStatus)
                        .isEqualTo(PaymentStatus.APPROVED);
                bothLookupsCompleted.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(paymentRepository).findByIdAndMerchantId(
                eq(paymentId),
                eq(MERCHANT_A));

        return lookupCount;
    }

    private List<MvcResult> concurrentResults(
            ExecutorService executor,
            Callable<MvcResult> first,
            Callable<MvcResult> second) throws Exception {
        Future<MvcResult> firstAttempt = executor.submit(first);
        Future<MvcResult> secondAttempt = executor.submit(second);
        return List.of(
                firstAttempt.get(20, TimeUnit.SECONDS),
                secondAttempt.get(20, TimeUnit.SECONDS));
    }

    private MvcResult resultWithStatus(List<MvcResult> results, int status) {
        return results.stream()
                .filter(result -> result.getResponse().getStatus() == status)
                .findFirst()
                .orElseThrow();
    }

    private Optional<PaymentEntity> findPayment(
            UUID paymentId,
            String merchantId) {
        return entityManager.createQuery("""
                        select payment
                        from PaymentEntity payment
                        where payment.id = :paymentId
                          and payment.merchantId = :merchantId
                        """, PaymentEntity.class)
                .setParameter("paymentId", paymentId)
                .setParameter("merchantId", merchantId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private void assertPersistedCounts(
            long payments,
            long refunds,
            long paymentEvents,
            long idempotencyRecords) {
        assertThat(paymentRepository.count()).isEqualTo(payments);
        assertThat(refundRepository.count()).isEqualTo(refunds);
        assertThat(paymentEventRepository.count()).isEqualTo(paymentEvents);
        assertThat(idempotencyRecordRepository.count())
                .isEqualTo(idempotencyRecords);
    }
}
