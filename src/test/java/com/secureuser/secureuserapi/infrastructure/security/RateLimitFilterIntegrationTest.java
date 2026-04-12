package com.secureuser.secureuserapi.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secureuser.secureuserapi.application.dto.AuthResponse;
import com.secureuser.secureuserapi.application.dto.LoginRequest;
import com.secureuser.secureuserapi.application.dto.RegisterRequest;
import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.application.service.RateLimiterService;
import com.secureuser.secureuserapi.application.service.UserLoginService;
import com.secureuser.secureuserapi.application.service.UserRegistrationService;
import com.secureuser.secureuserapi.infrastructure.web.AuthController;
import com.secureuser.secureuserapi.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Filter integration tests for {@link RateLimitFilter}.
 *
 * Uses {@code MockMvcBuilders.standaloneSetup} so that {@code RateLimitFilter}
 * is explicitly wired into the MockMvc filter chain — this is the correct
 * approach for slice-testing a custom {@code OncePerRequestFilter} without
 * loading a full Spring context.
 *
 * {@code RateLimiterService} is mocked (Mockito) to control allow/deny
 * decisions without real time-based sliding window state.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterIntegrationTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private UserLoginService userLoginService;

    @Mock
    private UserRegistrationService registrationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REGISTER_URL = "/api/v1/auth/register";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiterService, objectMapper);

        AuthController controller = new AuthController(registrationService, userLoginService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(rateLimitFilter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // 200 — allowed through rate limiter
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login succeeds when rate limiter allows the request")
    void login_rateLimiterAllows_returns200() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(true);
        when(userLoginService.login(any())).thenReturn(AuthResponse.of("jwt.token.here", "john_doe"));

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ─────────────────────────────────────────────────────────────────
    // 429 — rate limit exceeded
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login returns 429 when rate limiter denies the request")
    void login_rateLimiterDenies_returns429() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(any())).thenReturn(30L);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("429 response Retry-After header contains a numeric value")
    void login_rateLimited_retryAfterHeaderIsNumeric() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(any())).thenReturn(15L);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")));
    }

    @Test
    @DisplayName("429 response body does not expose client IP")
    void login_rateLimited_responseBodyDoesNotExposeIp() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(any())).thenReturn(10L);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(req -> { req.setRemoteAddr("1.2.3.4"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(not(containsString("1.2.3.4"))));
    }

    @Test
    @DisplayName("Rate-limited response body contains RATE_LIMITED code and no stack trace")
    void login_rateLimited_responseBodyHasSafeContent() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(any())).thenReturn(20L);

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error").exists())
                // No stack trace fields in response
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    // ─────────────────────────────────────────────────────────────────
    // Register endpoint — must NOT be rate limited
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register endpoint is NOT rate limited — rateLimiterService is never consulted")
    void register_notRateLimited_rateLimiterNeverCalled() throws Exception {
        UUID id = UUID.randomUUID();
        when(registrationService.register(any())).thenReturn(
                new UserResponse(id, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        RegisterRequest registerRequest = new RegisterRequest("john_doe", "john@example.com", "password123");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        verify(rateLimiterService, never()).isAllowed(any());
    }

    @Test
    @DisplayName("Rate limiter is called exactly once per login request")
    void login_rateLimiterCalledExactlyOnce() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(true);
        when(userLoginService.login(any())).thenReturn(AuthResponse.of("token", "user"));

        LoginRequest request = new LoginRequest("john_doe", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        verify(rateLimiterService, times(1)).isAllowed(any());
    }
}
