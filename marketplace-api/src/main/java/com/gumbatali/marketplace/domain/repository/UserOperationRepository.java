package com.gumbatali.marketplace.domain.repository;

import com.gumbatali.marketplace.domain.model.UserOperationEntity;
import com.gumbatali.marketplace.domain.model.UserOperationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOperationRepository extends JpaRepository<UserOperationEntity, UUID> {

    // Нужен для rate-limit: берем последнюю операцию пользователя по типу.
    Optional<UserOperationEntity> findTopByUserIdAndOperationTypeOrderByCreatedAtDesc(UUID userId,
                                                                                       UserOperationType operationType);
}
