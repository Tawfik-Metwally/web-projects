package io.github.tawfikmetwally.payments.payment.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEventEntity, UUID> {

    List<PaymentEventEntity> findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
            UUID paymentId,
            String merchantId);
}
