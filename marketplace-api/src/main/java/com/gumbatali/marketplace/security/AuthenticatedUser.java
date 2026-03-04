package com.gumbatali.marketplace.security;

import com.gumbatali.marketplace.domain.model.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String username, UserRole role) {
}
