package com.gumbatali.marketplace.domain.repository;

import com.gumbatali.marketplace.domain.model.PromoCodeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, UUID> {

    Optional<PromoCodeEntity> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCodeEntity p where p.code = :code")
    // FOR UPDATE при применении промокода, чтобы корректно менять current_uses.
    Optional<PromoCodeEntity> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCodeEntity p where p.id = :id")
    // FOR UPDATE при update/cancel заказа, если промокод уже привязан к заказу.
    Optional<PromoCodeEntity> findByIdForUpdate(@Param("id") UUID id);
}
