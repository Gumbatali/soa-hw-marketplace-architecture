package com.gumbatali.marketplace.web;

import com.gumbatali.marketplace.generated.api.AuthApi;
import com.gumbatali.marketplace.generated.model.AuthLoginRequest;
import com.gumbatali.marketplace.generated.model.AuthRefreshRequest;
import com.gumbatali.marketplace.generated.model.AuthRegisterRequest;
import com.gumbatali.marketplace.generated.model.AuthTokensResponse;
import com.gumbatali.marketplace.generated.model.UserResponse;
import com.gumbatali.marketplace.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<UserResponse> register(AuthRegisterRequest authRegisterRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(authRegisterRequest));
    }

    @Override
    public ResponseEntity<AuthTokensResponse> login(AuthLoginRequest authLoginRequest) {
        return ResponseEntity.ok(authService.login(authLoginRequest));
    }

    @Override
    public ResponseEntity<AuthTokensResponse> refresh(AuthRefreshRequest authRefreshRequest) {
        return ResponseEntity.ok(authService.refresh(authRefreshRequest));
    }
}
