package io.github.tawfikmetwally.payments.repository;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByMerchantIdAndOperationTypeAndIdempotencyKey(
            String merchantId, IdempotencyOperation operationType, String idempotencyKey);
}
