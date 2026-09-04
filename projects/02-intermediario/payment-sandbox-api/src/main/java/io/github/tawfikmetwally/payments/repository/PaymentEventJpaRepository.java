package io.github.tawfikmetwally.payments.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEventEntity, UUID> {

    List<PaymentEventEntity> findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
            UUID paymentId,
            String merchantId);
}
