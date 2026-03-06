package com.gumbatali.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Auth auth, Order order) {

    // Настройки JWT и TTL токенов.
    public record Auth(Integer accessTokenMinutes, Integer refreshTokenDays, String jwtSecret) {}

    // Настройки rate-limit для заказов.
    public record Order(Integer rateLimitMinutes) {}
}
