package com.secureuser.secureuserapi.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secureuser.secureuserapi.application.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter.
 *
 * Uses plain Mockito (no Spring context) to verify filter behaviour in isolation.
 * MockHttpServletResponse is used to capture response writes.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletRequest request;

    private MockHttpServletResponse response;
    private RateLimitFilter filter;

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String REMOTE_IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(rateLimiterService, objectMapper);
    }

    // ─────────────────────────────────────────────────────────────────
    // Non-login requests — must pass through without rate-limit check
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-login POST request passes through without rate limit check")
    void doFilterInternal_nonLoginPath_passesThrough() throws Exception {
        when(request.getServletPath()).thenReturn(REGISTER_PATH);
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    @DisplayName("GET request to login path passes through without rate limit check")
    void doFilterInternal_getOnLoginPath_passesThrough() throws Exception {
        // The filter evaluates method first (&&-short-circuit); path is never checked for non-POST
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    // ─────────────────────────────────────────────────────────────────
    // Login POST — allowed
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Allowed login request proceeds through filter chain")
    void doFilterInternal_loginAllowed_proceedsToChain() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(rateLimiterService.isAllowed(REMOTE_IP)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value()); // default, not 429
    }

    // ─────────────────────────────────────────────────────────────────
    // Login POST — rate limited
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rate-limited login request returns 429 with Retry-After header")
    void doFilterInternal_loginRateLimited_returns429() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(rateLimiterService.isAllowed(REMOTE_IP)).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(REMOTE_IP)).thenReturn(30L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Rate-limited response body contains RATE_LIMITED error code")
    void doFilterInternal_loginRateLimited_responseBodyHasRateLimitedCode() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(rateLimiterService.isAllowed(REMOTE_IP)).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(REMOTE_IP)).thenReturn(15L);

        filter.doFilterInternal(request, response, filterChain);

        String body = response.getContentAsString();
        assertThat(body).contains("RATE_LIMITED");
        assertThat(body).contains("error");
        assertThat(body).contains("timestamp");
    }

    @Test
    @DisplayName("Rate-limited response body does not expose client IP")
    void doFilterInternal_loginRateLimited_doesNotExposeIp() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(rateLimiterService.isAllowed(REMOTE_IP)).thenReturn(false);
        when(rateLimiterService.getRetryAfterSeconds(REMOTE_IP)).thenReturn(5L);

        filter.doFilterInternal(request, response, filterChain);

        String body = response.getContentAsString();
        assertThat(body).doesNotContain(REMOTE_IP);
    }

    // ─────────────────────────────────────────────────────────────────
    // X-Forwarded-For handling
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("X-Forwarded-For header is used as client IP when present")
    void doFilterInternal_xForwardedForPresent_usesForwardedIp() throws Exception {
        String forwardedIp = "203.0.113.5";
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedIp + ", 10.0.0.1");
        when(rateLimiterService.isAllowed(forwardedIp)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        // rateLimiterService must be called with the leftmost (client) IP, not the proxy IP
        verify(rateLimiterService).isAllowed(forwardedIp);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Falls back to remoteAddr when X-Forwarded-For is blank")
    void doFilterInternal_xForwardedForBlank_usesRemoteAddr() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("  ");
        when(request.getRemoteAddr()).thenReturn(REMOTE_IP);
        when(rateLimiterService.isAllowed(REMOTE_IP)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterService).isAllowed(REMOTE_IP);
    }

    @Test
    @DisplayName("Falls back to 'unknown' when both X-Forwarded-For and remoteAddr are null")
    void doFilterInternal_noIpAvailable_usesUnknown() throws Exception {
        when(request.getServletPath()).thenReturn(LOGIN_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(null);
        when(rateLimiterService.isAllowed("unknown")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterService).isAllowed("unknown");
    }
}
