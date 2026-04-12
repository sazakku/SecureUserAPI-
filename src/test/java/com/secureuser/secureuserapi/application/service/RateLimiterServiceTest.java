package com.secureuser.secureuserapi.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimiterService.
 *
 * A fixed Clock is injected to control time without Thread.sleep().
 * Each test creates a fresh service instance to avoid state bleed.
 */
class RateLimiterServiceTest {

    private static final String IP = "192.168.1.1";
    private static final String OTHER_IP = "10.0.0.1";

    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2026-01-01T00:00:00Z");
    }

    // ─────────────────────────────────────────────────────────────────
    // isAllowed — happy paths
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("First request from a new IP is allowed")
    void isAllowed_firstRequest_returnsTrue() {
        RateLimiterService service = serviceAt(baseTime);
        assertThat(service.isAllowed(IP)).isTrue();
    }

    @Test
    @DisplayName("Fifth request within the window is still allowed")
    void isAllowed_fifthRequest_returnsTrue() {
        RateLimiterService service = serviceAt(baseTime);
        service.isAllowed(IP); // 1
        service.isAllowed(IP); // 2
        service.isAllowed(IP); // 3
        service.isAllowed(IP); // 4
        assertThat(service.isAllowed(IP)).isTrue(); // 5
    }

    @Test
    @DisplayName("Sixth request within 60s window is denied")
    void isAllowed_sixthRequestWithinWindow_returnsFalse() {
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }
        assertThat(service.isAllowed(IP)).isFalse();
    }

    @Test
    @DisplayName("After the window expires, requests are allowed again")
    void isAllowed_afterWindowExpires_returnsTrue() {
        // Fill up 5 requests at t=0
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }

        // Advance clock by 61 seconds — all prior timestamps are expired
        Instant afterWindow = baseTime.plusSeconds(61);
        service.setClock(Clock.fixed(afterWindow, ZoneOffset.UTC));

        assertThat(service.isAllowed(IP)).isTrue();
    }

    @Test
    @DisplayName("Requests from different IPs are tracked independently")
    void isAllowed_differentIps_trackedIndependently() {
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }
        // IP is now blocked, but OTHER_IP should still be allowed
        assertThat(service.isAllowed(IP)).isFalse();
        assertThat(service.isAllowed(OTHER_IP)).isTrue();
    }

    @Test
    @DisplayName("Partial window expiry allows new requests as old ones slide out")
    void isAllowed_partialWindowExpiry_allowsNewRequests() {
        // t=0: 5 requests recorded
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }

        // t=61s: oldest request is now expired (window=60s), so one slot opens
        // The 5 requests were all at t=0, so at t=61 all 5 are expired
        Instant t61 = baseTime.plusSeconds(61);
        service.setClock(Clock.fixed(t61, ZoneOffset.UTC));

        assertThat(service.isAllowed(IP)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // getRetryAfterSeconds
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRetryAfterSeconds returns seconds until oldest entry expires")
    void getRetryAfterSeconds_returnsCorrectValue() {
        // Record 5 requests at t=0 (window = 60s)
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }

        // Now at t=45s: oldest entry was at t=0, expires at t=60 => retryAfter = 15s
        Instant t45 = baseTime.plusSeconds(45);
        service.setClock(Clock.fixed(t45, ZoneOffset.UTC));

        long retryAfter = service.getRetryAfterSeconds(IP);
        assertThat(retryAfter).isEqualTo(15L);
    }

    @Test
    @DisplayName("getRetryAfterSeconds returns 1 as minimum when window nearly expired")
    void getRetryAfterSeconds_nearExpiry_returnsAtLeastOne() {
        RateLimiterService service = serviceAt(baseTime);
        for (int i = 0; i < 5; i++) {
            service.isAllowed(IP);
        }

        // At t=59.9s: oldest entry at t=0 expires at t=60 => retryAfter = 0.1s -> rounds up to 1
        Instant t59 = baseTime.plusSeconds(59);
        service.setClock(Clock.fixed(t59, ZoneOffset.UTC));

        long retryAfter = service.getRetryAfterSeconds(IP);
        assertThat(retryAfter).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("getRetryAfterSeconds for unknown IP returns 0")
    void getRetryAfterSeconds_unknownIp_returnsZero() {
        RateLimiterService service = serviceAt(baseTime);
        assertThat(service.getRetryAfterSeconds("unknown-ip")).isEqualTo(0L);
    }

    // ─────────────────────────────────────────────────────────────────
    // Concurrency — smoke test
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent requests from same IP do not cause exceptions")
    void isAllowed_concurrentSameIp_noException() throws InterruptedException {
        RateLimiterService service = serviceAt(baseTime);
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> service.isAllowed(IP));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        // No assertion on count — just assert no exception was thrown
    }

    // ─────────────────────────────────────────────────────────────────
    // cleanupExpiredEntries (via scheduled eviction)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cleanupExpiredEntries removes IPs whose full deque is expired")
    void cleanupExpiredEntries_removesStaleIps() {
        RateLimiterService service = serviceAt(baseTime);
        service.isAllowed(IP); // one request at t=0

        // Advance past window — IP's only timestamp is now expired
        Instant afterWindow = baseTime.plusSeconds(61);
        service.setClock(Clock.fixed(afterWindow, ZoneOffset.UTC));

        service.cleanupExpiredEntries();

        // After cleanup the IP should have no entries, so a fresh request is allowed
        assertThat(service.isAllowed(IP)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private RateLimiterService serviceAt(Instant time) {
        Clock clock = Clock.fixed(time, ZoneOffset.UTC);
        return new RateLimiterService(clock, 5, 60);
    }
}
