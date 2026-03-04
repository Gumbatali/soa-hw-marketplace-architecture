package com.gumbatali.marketplace.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    PRODUCT_INACTIVE(HttpStatus.CONFLICT, "Product is inactive"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Order rate limit exceeded"),
    ORDER_HAS_ACTIVE(HttpStatus.CONFLICT, "User already has active order"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid order state transition"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),
    PROMO_CODE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Promo code invalid"),
    PROMO_CODE_MIN_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "Order amount below promo minimum"),
    ORDER_OWNERSHIP_VIOLATION(HttpStatus.FORBIDDEN, "Order belongs to another user"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token invalid"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token invalid"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
