package io.github.tawfikmetwally.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.InvalidPaymentStateTransitionException;

class PaymentTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "b3eb20ee-761f-4e53-a63a-48eb2a87c254");
    private static final Instant CREATED_AT = Instant.parse("2026-09-02T10:00:00Z");
    private static final Money MONEY = new Money(10_000, Currency.getInstance("BRL"));

    @Test
    void createsPaymentInPendingState() {
        Payment payment = newPendingPayment();

        assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
        assertThat(payment.getMerchantId()).isEqualTo("merchant-a");
        assertThat(payment.getMerchantReference()).isEqualTo("order-001");
        assertThat(payment.getMoney()).isEqualTo(MONEY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(payment.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void restoresExistingPaymentState() {
        Instant updatedAt = CREATED_AT.plusSeconds(60);

        Payment payment = Payment.restore(
                PAYMENT_ID,
                "merchant-a",
                "order-001",
                MONEY,
                PaymentStatus.APPROVED,
                CREATED_AT,
                updatedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(payment.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void rejectsInvalidMerchantId() {
        assertThatThrownBy(() -> Payment.create(
                PAYMENT_ID,
                " ",
                "order-001",
                MONEY,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchantId must not be blank");
        assertThatThrownBy(() -> Payment.create(
                PAYMENT_ID,
                "m".repeat(101),
                "order-001",
                MONEY,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchantId must not exceed 100 characters");
    }

    @Test
    void rejectsInvalidMerchantReference() {
        assertThatThrownBy(() -> Payment.create(
                PAYMENT_ID,
                "merchant-a",
                " ",
                MONEY,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchantReference must not be blank");
        assertThatThrownBy(() -> Payment.create(
                PAYMENT_ID,
                "merchant-a",
                "r".repeat(101),
                MONEY,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchantReference must not exceed 100 characters");
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThatThrownBy(() -> Payment.restore(
                PAYMENT_ID,
                "merchant-a",
                "order-001",
                MONEY,
                PaymentStatus.APPROVED,
                CREATED_AT,
                CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("updatedAt must not be before createdAt");
    }

    @Test
    void approvesPendingPaymentAndUpdatesTime() {
        Payment payment = newPendingPayment();

        payment.approve(CREATED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void declinesPendingPaymentAndUpdatesTime() {
        Payment payment = newPendingPayment();
        Instant declinedAt = CREATED_AT.plusSeconds(60);

        payment.decline(declinedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getUpdatedAt()).isEqualTo(declinedAt);
    }

    @Test
    void refundsApprovedPaymentAndUpdatesTime() {
        Payment payment = newPendingPayment();
        Instant approvedAt = CREATED_AT.plusSeconds(60);
        Instant refundedAt = CREATED_AT.plusSeconds(120);
        payment.approve(approvedAt);

        payment.refund(refundedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getUpdatedAt()).isEqualTo(refundedAt);
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingPayment() {
        Payment payment = newPendingPayment();
        Instant approvedAt = CREATED_AT.plusSeconds(60);
        payment.approve(approvedAt);

        assertThatThrownBy(() -> payment.decline(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidPaymentStateTransitionException.class)
                .hasMessage("Cannot transition payment from APPROVED to DECLINED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getUpdatedAt()).isEqualTo(approvedAt);
    }

    @Test
    void rejectsEarlierTransitionTimeWithoutChangingPayment() {
        Payment payment = newPendingPayment();

        assertThatThrownBy(() -> payment.approve(CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("occurredAt must not be before updatedAt");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    private Payment newPendingPayment() {
        return Payment.create(
                PAYMENT_ID,
                "merchant-a",
                "order-001",
                MONEY,
                CREATED_AT);
    }
}
