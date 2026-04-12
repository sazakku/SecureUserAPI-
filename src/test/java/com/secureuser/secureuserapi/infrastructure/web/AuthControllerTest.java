package com.secureuser.secureuserapi.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureuser.secureuserapi.application.dto.AuthResponse;
import com.secureuser.secureuserapi.application.dto.LoginRequest;
import com.secureuser.secureuserapi.application.dto.RegisterRequest;
import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.application.exception.DuplicateResourceException;
import com.secureuser.secureuserapi.application.service.UserLoginService;
import com.secureuser.secureuserapi.application.service.UserRegistrationService;
import com.secureuser.secureuserapi.infrastructure.security.JwtAuthenticationFilter;
import com.secureuser.secureuserapi.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for AuthController.
 *
 * Security setup:
 * - @AutoConfigureMockMvc(addFilters = false) prevents JwtAuthenticationFilter
 *   from being added to the MockMvc filter chain, so it never intercepts requests.
 * - TestSecurityConfig provides a permitAll security chain so Spring Security
 *   auto-config does not return 401/403 for the unauthenticated register endpoint.
 * - @MockitoBean for JwtTokenProvider and UserDetailsService satisfies the
 *   dependency graph wired by the auto-configured security beans.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRegistrationService registrationService;

    @MockitoBean
    private UserLoginService userLoginService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";

    // ═════════════════════════════════════════════════════════════════════════
    // POST /register tests
    // ═════════════════════════════════════════════════════════════════════════

    // ── 201 Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 201 with valid payload")
    void register_validPayload_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(id, "john_doe", "john@example.com", Set.of("ROLE_USER"));
        when(registrationService.register(any())).thenReturn(response);

        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.username").value("john_doe"))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ── 400 Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 400 when username is blank")
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /register: 400 when username is too short (< 3 chars)")
    void register_usernameTooShort_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("ab", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /register: 400 when username is too long (> 50 chars)")
    void register_usernameTooLong_returns400() throws Exception {
        String longUsername = "a".repeat(51);
        RegisterRequest request = new RegisterRequest(longUsername, "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /register: 400 when username contains invalid characters")
    void register_usernameWithInvalidChars_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("john doe!", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /register: 400 when email is blank")
    void register_blankEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("john_doe", "", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("email")));
    }

    @Test
    @DisplayName("POST /register: 400 when email is malformed")
    void register_malformedEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("john_doe", "not-an-email", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("email")));
    }

    @Test
    @DisplayName("POST /register: 400 when email exceeds 100 characters")
    void register_emailTooLong_returns400() throws Exception {
        String longEmail = "a".repeat(90) + "@example.com";
        RegisterRequest request = new RegisterRequest("john_doe", longEmail, "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("email")));
    }

    @Test
    @DisplayName("POST /register: 400 when password is blank")
    void register_blankPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /register: 400 when password is too short (< 8 chars)")
    void register_passwordTooShort_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "short");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /register: 400 when password exceeds 128 characters")
    void register_passwordTooLong_returns400() throws Exception {
        String longPassword = "a".repeat(129);
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", longPassword);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /register: 400 when request body is missing")
    void register_missingBody_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 409 when username is already taken")
    void register_duplicateUsername_returns409() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DuplicateResourceException("Username is already taken"));

        RegisterRequest request = new RegisterRequest("existing_user", "new@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error").value("Username is already taken"));
    }

    @Test
    @DisplayName("POST /register: 409 when email is already in use")
    void register_duplicateEmail_returns409() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DuplicateResourceException("Email is already in use"));

        RegisterRequest request = new RegisterRequest("new_user", "existing@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error").value("Email is already in use"));
    }

    // ── 500 Internal error ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 500 when ROLE_USER is missing from database")
    void register_roleUserMissing_returns500() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new IllegalStateException(
                        "Default role ROLE_USER not found. Ensure roles are seeded."));

        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").value("An unexpected error occurred"));
    }

    // ── 409 Race condition ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 409 on race condition (concurrent duplicate bypasses existsBy checks)")
    void register_raceCondition_returns409() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error").value("Resource already exists"));
    }

    // ── No sensitive data in response ─────────────────────────────────────────

    @Test
    @DisplayName("POST /register: response never contains password field")
    void register_responseNeverContainsPassword() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(id, "john_doe", "john@example.com", Set.of("ROLE_USER"));
        when(registrationService.register(any())).thenReturn(response);

        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // POST /login tests
    // ═════════════════════════════════════════════════════════════════════════

    // ── 200 Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 200 with valid credentials")
    void login_validCredentials_returns200() throws Exception {
        AuthResponse authResponse = AuthResponse.of("jwt.token.here", "john_doe");
        when(userLoginService.login(any())).thenReturn(authResponse);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("john_doe"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ── 400 Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 400 when username is blank")
    void login_blankUsername_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /login: 400 when username exceeds 50 characters")
    void login_usernameTooLong_returns400() throws Exception {
        String longUsername = "a".repeat(51);
        LoginRequest request = new LoginRequest(longUsername, "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    @Test
    @DisplayName("POST /login: 400 when password is blank")
    void login_blankPassword_returns400() throws Exception {
        LoginRequest request = new LoginRequest("john_doe", "");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /login: 400 when password is too short (< 8 chars)")
    void login_passwordTooShort_returns400() throws Exception {
        LoginRequest request = new LoginRequest("john_doe", "short");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /login: 400 when password exceeds 128 characters")
    void login_passwordTooLong_returns400() throws Exception {
        String longPassword = "a".repeat(129);
        LoginRequest request = new LoginRequest("john_doe", longPassword);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("password")));
    }

    @Test
    @DisplayName("POST /login: 400 when request body is missing")
    void login_missingBody_returns400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── 401 Bad credentials ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 401 when credentials are wrong")
    void login_badCredentials_returns401() throws Exception {
        when(userLoginService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("john_doe", "wrongpassword");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error").value("Authentication failed"));
    }

    // ── No sensitive data in response ─────────────────────────────────────────

    @Test
    @DisplayName("POST /login: response never contains password field")
    void login_responseNeverContainsPassword() throws Exception {
        AuthResponse authResponse = AuthResponse.of("jwt.token.here", "john_doe");
        when(userLoginService.login(any())).thenReturn(authResponse);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ── 400 Username too short ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 400 when username is too short (< 3 chars)")
    void login_usernameTooShort_returns400() throws Exception {
        LoginRequest request = new LoginRequest("ab", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    // ── 400 Username @Pattern violation ──────────────────────────────────────

    @Test
    @DisplayName("POST /login: 400 when username contains characters outside [a-zA-Z0-9_]")
    void login_usernameWithInvalidChars_returns400() throws Exception {
        // Spaces, hyphens, and special characters are rejected by
        // @Pattern(regexp = "^[a-zA-Z0-9_]+$") on LoginRequest.username
        LoginRequest request = new LoginRequest("john doe!", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", containsString("username")));
    }

    // ── 500 Unexpected error ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 500 body contains safe generic message, not stack trace")
    void login_unexpectedException_returns500WithSafeBody() throws Exception {
        when(userLoginService.login(any()))
                .thenThrow(new RuntimeException("internal details must not leak"));

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").value("An unexpected error occurred"));
    }
}
