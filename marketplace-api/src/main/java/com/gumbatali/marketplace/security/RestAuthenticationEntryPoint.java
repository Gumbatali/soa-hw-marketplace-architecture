package com.gumbatali.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.generated.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_ERROR_CODE_ATTR = "auth_error_code";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.TOKEN_INVALID;
        Object attribute = request.getAttribute(AUTH_ERROR_CODE_ATTR);
        if (attribute instanceof ErrorCode attrCode) {
            errorCode = attrCode;
        }

        ErrorResponse body = new ErrorResponse(
            com.gumbatali.marketplace.generated.model.ErrorCode.valueOf(errorCode.name()),
            errorCode.defaultMessage()
        );
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
