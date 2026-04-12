package com.secureuser.secureuserapi.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureuser.secureuserapi.application.dto.ApiError;
import com.secureuser.secureuserapi.application.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces a per-IP sliding window rate limit on
 * {@code POST /api/v1/auth/login}.
 *
 * Registered before {@link JwtAuthenticationFilter} in {@code SecurityConfig}
 * so that brute-force login attempts are rejected before any authentication
 * logic or DB lookup executes.
 *
 * When the limit is exceeded the filter writes a 429 response with:
 * <ul>
 *   <li>{@code Retry-After} header (seconds until a slot opens)</li>
 *   <li>JSON body matching the project {@code ApiError} envelope</li>
 * </ul>
 *
 * Client IP resolution strategy:
 * <ol>
 *   <li>First value in {@code X-Forwarded-For} header (leftmost = original client).</li>
 *   <li>Falls back to {@code HttpServletRequest#getRemoteAddr()}.</li>
 *   <li>Falls back to {@code "unknown"} if both are null/blank to prevent NPE.</li>
 * </ol>
 *
 * Note: {@code X-Forwarded-For} can be spoofed if the app is not behind a
 * trusted reverse proxy. This risk is accepted for the portfolio deployment
 * model and is documented in {@code docs/design/login-rate-limiting.md}.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String POST_METHOD = "POST";

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!isLoginEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (rateLimiterService.isAllowed(clientIp)) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = rateLimiterService.getRetryAfterSeconds(clientIp);
            writeRateLimitResponse(response, retryAfterSeconds);
        }
    }

    private boolean isLoginEndpoint(HttpServletRequest request) {
        if (!POST_METHOD.equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        // getServletPath() is populated in a real servlet container; getRequestURI() is
        // always reliable in both container and MockMvc standalone test contexts.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return LOGIN_PATH.equals(path);
    }

    /**
     * Resolves the effective client IP.
     *
     * Uses only the first (leftmost) value in {@code X-Forwarded-For} to avoid
     * trusting a chain of proxies. An empty or blank header is treated as absent.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // Take the leftmost IP (original client); trim whitespace
            return forwarded.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        if (StringUtils.hasText(remoteAddr)) {
            return remoteAddr;
        }

        return "unknown";
    }

    private void writeRateLimitResponse(HttpServletResponse response,
                                        long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ApiError error = ApiError.of(
                "Rate limit exceeded. Too many login attempts. Please try again later.",
                "RATE_LIMITED"
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
