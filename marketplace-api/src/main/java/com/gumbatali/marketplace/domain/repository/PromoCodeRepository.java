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
    Optional<PromoCodeEntity> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCodeEntity p where p.id = :id")
    Optional<PromoCodeEntity> findByIdForUpdate(@Param("id") UUID id);
}
