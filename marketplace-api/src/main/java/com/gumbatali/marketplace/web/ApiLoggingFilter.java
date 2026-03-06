package com.gumbatali.marketplace.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gumbatali.marketplace.security.AuthenticatedUser;
import com.gumbatali.marketplace.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiLoggingFilter.class);
    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "DELETE");

    private final ObjectMapper objectMapper;

    public ApiLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // request_id генерируется на каждый запрос и возвращается в ответе.
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            // Формируем JSON-лог в едином формате.
            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("request_id", requestId);
            logEntry.put("method", request.getMethod());
            logEntry.put("endpoint", request.getRequestURI());
            logEntry.put("status_code", response.getStatus());
            logEntry.put("duration_ms", System.currentTimeMillis() - start);

            AuthenticatedUser user = SecurityUtils.currentUser();
            logEntry.put("user_id", user != null ? user.id() : null);
            logEntry.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC));

            if (MUTATING_METHODS.contains(request.getMethod())) {
                // Для POST/PUT/DELETE логируем body, но маскируем чувствительные поля.
                String rawBody = extractBody(wrappedRequest);
                if (!rawBody.isBlank()) {
                    logEntry.put("request_body", maskSensitiveBody(rawBody));
                }
            }

            LOG.info(writeLog(logEntry));
        }
    }

    private String extractBody(ContentCachingRequestWrapper request) {
        byte[] body = request.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private Object maskSensitiveBody(String rawBody) {
        try {
            Object payload = objectMapper.readValue(rawBody, Object.class);
            return maskRecursive(payload);
        } catch (JsonProcessingException ignored) {
            // Fallback для не-JSON payload.
            return rawBody.replaceAll("(?i)(\\\"password\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskRecursive(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if ("password".equalsIgnoreCase(key)) {
                    masked.put(key, "***");
                } else {
                    masked.put(key, maskRecursive(child));
                }
            }
            return masked;
        }

        if (value instanceof List<?> list) {
            return list.stream().map(this::maskRecursive).toList();
        }

        return value;
    }

    private String writeLog(Map<String, Object> logEntry) {
        try {
            return objectMapper.writeValueAsString(logEntry);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"failed_to_serialize_log\"}";
        }
    }
}
