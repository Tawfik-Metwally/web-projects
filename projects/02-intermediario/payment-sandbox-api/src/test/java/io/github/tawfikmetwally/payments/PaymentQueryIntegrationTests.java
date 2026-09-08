package io.github.tawfikmetwally.payments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@ActiveProfiles("demo-no-auth")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class PaymentQueryIntegrationTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_A = "merchant-query-a";
    private static final String MERCHANT_B = "merchant-query-b";
    private static final Instant BASE_TIME = Instant.parse("2026-09-08T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentEventJpaRepository paymentEventRepository;

    @Autowired
    private RefundJpaRepository refundRepository;

    @Autowired
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

    @BeforeEach
    void clearTemporaryDatabase() {
        idempotencyRecordRepository.deleteAllInBatch();
        refundRepository.deleteAllInBatch();
        paymentEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
    }

    @Test
    void returnsPaymentByIdForItsMerchant() throws Exception {
        PaymentEntity payment = savePayment(
                MERCHANT_A,
                "ORDER-OWN-001",
                10_000,
                PaymentStatus.APPROVED,
                BASE_TIME);

        mockMvc.perform(get(ENDPOINT + "/{paymentId}", payment.getId())
                        .header("X-Demo-Merchant-Id", MERCHANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.merchantReference").value("ORDER-OWN-001"))
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void hidesPaymentThatBelongsToAnotherMerchant() throws Exception {
        PaymentEntity payment = savePayment(
                MERCHANT_A,
                "ORDER-PRIVATE-001",
                10_000,
                PaymentStatus.APPROVED,
                BASE_TIME);

        mockMvc.perform(get(ENDPOINT + "/{paymentId}", payment.getId())
                        .header("X-Demo-Merchant-Id", MERCHANT_B))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void listsOnlyPaymentsFromAuthenticatedMerchant() throws Exception {
        savePayment(MERCHANT_A, "ORDER-A-OLDER", 10_000,
                PaymentStatus.APPROVED, BASE_TIME);
        savePayment(MERCHANT_B, "ORDER-B-PRIVATE", 20_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(60));
        savePayment(MERCHANT_A, "ORDER-A-NEWER", 30_000,
                PaymentStatus.DECLINED, BASE_TIME.plusSeconds(120));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].merchantReference")
                        .value("ORDER-A-NEWER"))
                .andExpect(jsonPath("$.content[1].merchantReference")
                        .value("ORDER-A-OLDER"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void ordersPaymentsFromNewestToOldest() throws Exception {
        savePayment(MERCHANT_A, "ORDER-OLDEST", 10_000,
                PaymentStatus.APPROVED, BASE_TIME);
        savePayment(MERCHANT_A, "ORDER-NEWEST", 30_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(120));
        savePayment(MERCHANT_A, "ORDER-MIDDLE", 20_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(60));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].merchantReference")
                        .value("ORDER-NEWEST"))
                .andExpect(jsonPath("$.content[1].merchantReference")
                        .value("ORDER-MIDDLE"))
                .andExpect(jsonPath("$.content[2].merchantReference")
                        .value("ORDER-OLDEST"));
    }

    @Test
    void paginatesPaymentsAndReturnsConsistentMetadata() throws Exception {
        savePaymentsForPagination();

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].merchantReference").value("ORDER-5"))
                .andExpect(jsonPath("$.content[1].merchantReference").value("ORDER-4"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].merchantReference").value("ORDER-3"))
                .andExpect(jsonPath("$.content[1].merchantReference").value("ORDER-2"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].merchantReference").value("ORDER-1"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void filtersPaymentsByStatusAndMerchant() throws Exception {
        savePayment(MERCHANT_A, "ORDER-A-APPROVED", 10_000,
                PaymentStatus.APPROVED, BASE_TIME);
        savePayment(MERCHANT_A, "ORDER-A-DECLINED", 20_000,
                PaymentStatus.DECLINED, BASE_TIME.plusSeconds(60));
        savePayment(MERCHANT_B, "ORDER-B-APPROVED", 30_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(120));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].merchantReference")
                        .value("ORDER-A-APPROVED"))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void returnsEmptyContentWhenPageIsBeyondLastPage() throws Exception {
        savePayment(MERCHANT_A, "ORDER-1", 10_000,
                PaymentStatus.APPROVED, BASE_TIME);
        savePayment(MERCHANT_A, "ORDER-2", 20_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(60));
        savePayment(MERCHANT_A, "ORDER-3", 30_000,
                PaymentStatus.APPROVED, BASE_TIME.plusSeconds(120));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Demo-Merchant-Id", MERCHANT_A)
                        .param("page", "4")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    private void savePaymentsForPagination() {
        paymentRepository.saveAllAndFlush(List.of(
                payment("ORDER-1", 10_000, BASE_TIME),
                payment("ORDER-2", 20_000, BASE_TIME.plusSeconds(60)),
                payment("ORDER-3", 30_000, BASE_TIME.plusSeconds(120)),
                payment("ORDER-4", 40_000, BASE_TIME.plusSeconds(180)),
                payment("ORDER-5", 50_000, BASE_TIME.plusSeconds(240))));
    }

    private PaymentEntity savePayment(
            String merchantId,
            String merchantReference,
            long amountMinor,
            PaymentStatus status,
            Instant createdAt) {
        return paymentRepository.saveAndFlush(new PaymentEntity(
                UUID.randomUUID(),
                merchantId,
                merchantReference,
                amountMinor,
                "BRL",
                status,
                createdAt,
                createdAt));
    }

    private PaymentEntity payment(
            String merchantReference,
            long amountMinor,
            Instant createdAt) {
        return new PaymentEntity(
                UUID.randomUUID(),
                MERCHANT_A,
                merchantReference,
                amountMinor,
                "BRL",
                PaymentStatus.APPROVED,
                createdAt,
                createdAt);
    }
}
