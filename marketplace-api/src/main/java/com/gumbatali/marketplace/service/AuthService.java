package com.gumbatali.marketplace.service;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.config.AppProperties;
import com.gumbatali.marketplace.domain.model.RefreshTokenEntity;
import com.gumbatali.marketplace.domain.model.UserEntity;
import com.gumbatali.marketplace.domain.repository.RefreshTokenRepository;
import com.gumbatali.marketplace.domain.repository.UserRepository;
import com.gumbatali.marketplace.generated.model.AuthLoginRequest;
import com.gumbatali.marketplace.generated.model.AuthRefreshRequest;
import com.gumbatali.marketplace.generated.model.AuthRegisterRequest;
import com.gumbatali.marketplace.generated.model.AuthTokensResponse;
import com.gumbatali.marketplace.generated.model.UserResponse;
import com.gumbatali.marketplace.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final ApiMapper apiMapper;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties appProperties,
                       ApiMapper apiMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.apiMapper = apiMapper;
    }

    @Transactional
    public UserResponse register(AuthRegisterRequest request) {
        // Простая защита от дублей на уровне бизнес-логики.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Username already exists",
                Map.of("username", "already_exists")
            );
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(apiMapper.toDomainRole(request.getRole()));
        UserEntity saved = userRepository.save(user);
        return apiMapper.toUserResponse(saved);
    }

    @Transactional
    public AuthTokensResponse login(AuthLoginRequest request) {
        // В учебном проекте объединяем ошибки "не найден" и "неверный пароль" в TOKEN_INVALID.
        UserEntity user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthTokensResponse refresh(AuthRefreshRequest request) {
        UUID userId;
        try {
            // Проверяем подпись/срок действия и тип токена (должен быть REFRESH).
            userId = jwtService.parseRefreshToken(request.getRefreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // В БД хранится hash refresh-токена: это безопаснее, чем хранить token как есть.
        String refreshHash = jwtService.hashToken(request.getRefreshToken());
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(refreshHash)
            .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!refreshToken.getUser().getId().equals(userId) || refreshToken.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        refreshTokenRepository.delete(refreshToken);
        return issueTokens(refreshToken.getUser());
    }

    private AuthTokensResponse issueTokens(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Чистим старые refresh-токены, чтобы таблица не росла бесконечно.
        refreshTokenRepository.deleteAllByExpiresAtBefore(now);

        OffsetDateTime accessExpiresAt = now.plusMinutes(appProperties.auth().accessTokenMinutes());
        OffsetDateTime refreshExpiresAt = now.plusDays(appProperties.auth().refreshTokenDays());

        String accessToken = jwtService.generateAccessToken(user, accessExpiresAt);
        String refreshToken = jwtService.generateRefreshToken(user, refreshExpiresAt);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(jwtService.hashToken(refreshToken));
        refreshTokenEntity.setExpiresAt(refreshExpiresAt);
        refreshTokenRepository.save(refreshTokenEntity);

        AuthTokensResponse response = new AuthTokensResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setAccessExpiresAt(accessExpiresAt);
        response.setRefreshExpiresAt(refreshExpiresAt);
        return response;
    }
}
