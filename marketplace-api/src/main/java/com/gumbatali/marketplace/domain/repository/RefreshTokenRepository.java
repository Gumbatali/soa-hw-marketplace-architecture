package com.gumbatali.marketplace.domain.repository;

import com.gumbatali.marketplace.domain.model.RefreshTokenEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    void deleteAllByExpiresAtBefore(OffsetDateTime dateTime);
}
