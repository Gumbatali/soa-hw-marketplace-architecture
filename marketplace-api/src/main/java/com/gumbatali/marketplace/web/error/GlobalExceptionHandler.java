package com.gumbatali.marketplace.web.error;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.generated.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        // Контрактные бизнес-ошибки, которые мы сами бросаем в сервисах.
        return build(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        // Ошибки @Valid по body: собираем по полям.
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.computeIfAbsent(fieldError.getField(), ignored -> new java.util.ArrayList<String>());
            @SuppressWarnings("unchecked")
            var messages = (java.util.List<String>) details.get(fieldError.getField());
            messages.add(fieldError.getDefaultMessage());
        }
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        // Ошибки валидации query/path параметров.
        Map<String, Object> details = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getMessage(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        // Невалидный JSON или несовместимые типы в body.
        return build(
            ErrorCode.VALIDATION_ERROR,
            ErrorCode.VALIDATION_ERROR.defaultMessage(),
            Map.of("body", "invalid_json_or_type")
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Ошибки ограничений БД (unique/fk/not null и т.д.).
        return build(
            ErrorCode.VALIDATION_ERROR,
            ErrorCode.VALIDATION_ERROR.defaultMessage(),
            Map.of("db", "constraint_violation")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Safety-net: не раскрываем внутренние детали наружу.
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, Map<String, Object> details) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(com.gumbatali.marketplace.generated.model.ErrorCode.valueOf(errorCode.name()));
        response.setMessage(message);
        response.setDetails(details);
        return ResponseEntity.status(errorCode.httpStatus()).body(response);
    }
}
