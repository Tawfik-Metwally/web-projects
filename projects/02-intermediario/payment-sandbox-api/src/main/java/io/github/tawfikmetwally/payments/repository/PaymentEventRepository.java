package io.github.tawfikmetwally.payments.repository;

import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {

    List<PaymentEventEntity> findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
            UUID paymentId, String merchantId);
}
