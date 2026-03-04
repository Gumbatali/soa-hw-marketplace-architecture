package com.gumbatali.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Auth auth, Order order) {

    public record Auth(Integer accessTokenMinutes, Integer refreshTokenDays, String jwtSecret) {}

    public record Order(Integer rateLimitMinutes) {}
}
