package io.github.tawfikmetwally.payments.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByMerchantIdAndOperationTypeAndIdempotencyKey(
            String merchantId,
            IdempotencyOperation operationType,
            String idempotencyKey);
}
