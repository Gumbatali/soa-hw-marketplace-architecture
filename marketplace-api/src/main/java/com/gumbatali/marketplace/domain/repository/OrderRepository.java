package com.gumbatali.marketplace.domain.repository;

import com.gumbatali.marketplace.domain.model.OrderEntity;
import com.gumbatali.marketplace.domain.model.OrderStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID>, JpaSpecificationExecutor<OrderEntity> {

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "promoCode"})
    Optional<OrderEntity> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "promoCode"})
    @Query("select o from OrderEntity o where o.id = :id")
    // FOR UPDATE: используем в update/cancel/status, чтобы избежать гонок по одному заказу.
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"items", "promoCode"})
    Page<OrderEntity> findAll(Specification<OrderEntity> spec, Pageable pageable);
}
