package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@ActiveProfiles("demo-no-auth")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class PaymentCreationIntegrationTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_ID = "merchant-integration";
    private static final String IDEMPOTENCY_KEY = "idem-integration-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentEventJpaRepository paymentEventRepository;

    @Autowired
    private RefundJpaRepository refundRepository;

    @MockitoSpyBean
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
    void createsPaymentEventsAndIdempotencyRecord() throws Exception {
        MvcResult creation = mockMvc.perform(request(IDEMPOTENCY_KEY, 10_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.merchantReference").value("ORDER-INTEGRATION-123"))
                .andReturn();

        UUID paymentId = paymentIdFromLocation(creation);
        PaymentEntity payment = paymentRepository
                .findByIdAndMerchantId(paymentId, MERCHANT_ID)
                .orElseThrow();

        assertThat(payment.getAmountMinor()).isEqualTo(10_000);
        assertThat(payment.getCurrency()).isEqualTo("BRL");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentEventRepository
                        .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                                paymentId,
                                MERCHANT_ID))
                .extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder(
                        PaymentEventType.PAYMENT_CREATED,
                        PaymentEventType.PAYMENT_APPROVED);
        assertThat(idempotencyRecordRepository
                        .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                                MERCHANT_ID,
                                IdempotencyOperation.CREATE_PAYMENT,
                                IDEMPOTENCY_KEY))
                .isPresent();
    }

    @Test
    void replaysIdenticalRequestWithoutDuplicatingPersistedData() throws Exception {
        MvcResult creation = mockMvc.perform(request(IDEMPOTENCY_KEY, 10_000))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = paymentIdFromLocation(creation);

        mockMvc.perform(request(IDEMPOTENCY_KEY, 10_000))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(paymentId.toString()));

        assertPersistedCounts(1, 2, 1);
    }

    @Test
    void rejectsChangedRequestWithSameKeyWithoutPersistingPartialData() throws Exception {
        MvcResult creation = mockMvc.perform(request(IDEMPOTENCY_KEY, 10_000))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = paymentIdFromLocation(creation);

        mockMvc.perform(request(IDEMPOTENCY_KEY, 20_000))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        assertPersistedCounts(1, 2, 1);
        assertThat(paymentRepository
                        .findByIdAndMerchantId(paymentId, MERCHANT_ID))
                .isPresent()
                .get()
                .extracting(PaymentEntity::getAmountMinor)
                .isEqualTo(10_000L);
    }

    @Test
    void replaysTheLosingConcurrentRequestAfterRealUniqueConstraintRollback()
            throws Exception {
        String concurrentKey = "idem-concurrent-123";
        CyclicBarrier bothInitialLookupsCompleted = new CyclicBarrier(2);
        AtomicInteger lookupCount = new AtomicInteger();

        doAnswer(invocation -> {
            int currentLookup = lookupCount.incrementAndGet();
            String merchantId = invocation.getArgument(0);
            IdempotencyOperation operation = invocation.getArgument(1);
            String idempotencyKey = invocation.getArgument(2);
            Optional<IdempotencyRecordEntity> result = findIdempotencyRecord(
                    merchantId,
                    operation,
                    idempotencyKey);
            if (currentLookup <= 2) {
                assertThat(result).isEmpty();
                bothInitialLookupsCompleted.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(idempotencyRecordRepository)
                .findByMerchantIdAndOperationTypeAndIdempotencyKey(
                        eq(MERCHANT_ID),
                        eq(IdempotencyOperation.CREATE_PAYMENT),
                        eq(concurrentKey));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> createPayment = () -> mockMvc
                    .perform(request(concurrentKey, 10_000))
                    .andReturn();

            Future<MvcResult> firstAttempt = executor.submit(createPayment);
            Future<MvcResult> secondAttempt = executor.submit(createPayment);
            List<MvcResult> results = List.of(
                    firstAttempt.get(20, TimeUnit.SECONDS),
                    secondAttempt.get(20, TimeUnit.SECONDS));

            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 200);

            MvcResult created = resultWithStatus(results, 201);
            MvcResult replayed = resultWithStatus(results, 200);
            String createdPaymentId = JsonPath.read(
                    created.getResponse().getContentAsString(),
                    "$.id");
            String replayedPaymentId = JsonPath.read(
                    replayed.getResponse().getContentAsString(),
                    "$.id");

            assertThat(replayedPaymentId).isEqualTo(createdPaymentId);
            assertThat(replayed.getResponse().getHeader("Idempotency-Replayed"))
                    .isEqualTo("true");
            assertThat(lookupCount).hasValue(3);
            assertPersistedCounts(1, 2, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequestBuilder request(String idempotencyKey, long amount) {
        return post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Demo-Merchant-Id", MERCHANT_ID)
                .header("Idempotency-Key", idempotencyKey)
                .content("""
                        {
                          "amount": %d,
                          "currency": "BRL",
                          "merchantReference": "ORDER-INTEGRATION-123",
                          "paymentMethodToken": "tok_approved"
                        }
                        """.formatted(amount));
    }

    private UUID paymentIdFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull().startsWith(ENDPOINT + "/");
        return UUID.fromString(location.substring((ENDPOINT + "/").length()));
    }

    private MvcResult resultWithStatus(List<MvcResult> results, int status) {
        return results.stream()
                .filter(result -> result.getResponse().getStatus() == status)
                .findFirst()
                .orElseThrow();
    }

    private Optional<IdempotencyRecordEntity> findIdempotencyRecord(
            String merchantId,
            IdempotencyOperation operation,
            String idempotencyKey) {
        return entityManager.createQuery("""
                        select record
                        from IdempotencyRecordEntity record
                        join fetch record.payment
                        where record.merchantId = :merchantId
                          and record.operationType = :operation
                          and record.idempotencyKey = :idempotencyKey
                        """, IdempotencyRecordEntity.class)
                .setParameter("merchantId", merchantId)
                .setParameter("operation", operation)
                .setParameter("idempotencyKey", idempotencyKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private void assertPersistedCounts(
            long payments,
            long paymentEvents,
            long idempotencyRecords) {
        assertThat(paymentRepository.count()).isEqualTo(payments);
        assertThat(paymentEventRepository.count()).isEqualTo(paymentEvents);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyRecords);
    }
}
