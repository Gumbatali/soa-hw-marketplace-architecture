package com.gumbatali.marketplace.security;

import com.gumbatali.marketplace.config.AppProperties;
import com.gumbatali.marketplace.domain.model.UserEntity;
import com.gumbatali.marketplace.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ROLE_CLAIM = "role";
    private static final String USERNAME_CLAIM = "username";

    private final SecretKey key;

    public JwtService(AppProperties appProperties) {
        byte[] secretBytes = appProperties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(UserEntity user, OffsetDateTime expiresAt) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim(USERNAME_CLAIM, user.getUsername())
            .claim(ROLE_CLAIM, user.getRole().name())
            .claim(TOKEN_TYPE_CLAIM, "ACCESS")
            .issuedAt(new Date())
            .expiration(Date.from(expiresAt.toInstant()))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(UserEntity user, OffsetDateTime expiresAt) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim(ROLE_CLAIM, user.getRole().name())
            .claim(TOKEN_TYPE_CLAIM, "REFRESH")
            .claim("jti", UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(Date.from(expiresAt.toInstant()))
            .signWith(key)
            .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) throws ExpiredJwtException, JwtException {
        Claims claims = parseClaims(token);
        if (!"ACCESS".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Invalid token type");
        }
        UUID id = UUID.fromString(claims.getSubject());
        String username = claims.get(USERNAME_CLAIM, String.class);
        UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
        return new AuthenticatedUser(id, username, role);
    }

    public UUID parseRefreshToken(String token) throws ExpiredJwtException, JwtException {
        Claims claims = parseClaims(token);
        if (!"REFRESH".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Invalid token type");
        }
        return UUID.fromString(claims.getSubject());
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
