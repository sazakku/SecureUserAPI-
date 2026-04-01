package com.secureuser.secureuserapi.infrastructure.web;

import com.secureuser.secureuserapi.application.dto.ApiResponse;
import com.secureuser.secureuserapi.application.dto.AuthResponse;
import com.secureuser.secureuserapi.application.dto.LoginRequest;
import com.secureuser.secureuserapi.application.dto.RegisterRequest;
import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.application.service.UserLoginService;
import com.secureuser.secureuserapi.application.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: rate limiting — apply per-IP limit (e.g. 5 req/min) before production deployment

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRegistrationService registrationService;
    private final UserLoginService userLoginService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse userResponse = registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(userResponse, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = userLoginService.login(request);

        return ResponseEntity
                .ok(ApiResponse.ok(authResponse, "Login successful"));
    }
}
