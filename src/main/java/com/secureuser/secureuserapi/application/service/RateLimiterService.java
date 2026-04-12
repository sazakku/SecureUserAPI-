package com.secureuser.secureuserapi.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter.
 *
 * Tracks per-IP request timestamps in a {@code ConcurrentHashMap<String, Deque<Instant>>}.
 * Each call to {@link #isAllowed(String)} atomically prunes expired entries,
 * checks the count against {@code maxRequests}, and—if allowed—appends the
 * current timestamp.
 *
 * A scheduled cleanup task runs every 5 minutes to evict IPs whose entire
 * deque has expired, bounding memory growth under sustained attack traffic.
 *
 * The {@code Clock} dependency is injected to allow deterministic unit testing
 * without {@code Thread.sleep()}.
 *
 * No {@code @PreAuthorize} is applied — this service is called by a servlet
 * filter before Spring Security context is populated (see AD-11 in design doc).
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final Duration windowSize;
    private Clock clock;

    /**
     * Primary constructor used by Spring (reads configuration from
     * {@code application.properties}).
     */
    public RateLimiterService(
            Clock clock,
            @Value("${rate-limit.login.max-requests:5}") int maxRequests,
            @Value("${rate-limit.login.window-seconds:60}") long windowSeconds) {
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.windowSize = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Determines whether the given client IP is within the allowed request rate.
     *
     * If allowed, the current timestamp is recorded and {@code true} is returned.
     * If the limit is already reached, no timestamp is recorded and {@code false}
     * is returned.
     *
     * @param clientIp the resolved client IP address
     * @return {@code true} if the request should proceed; {@code false} if it
     *         should be rejected with 429
     */
    public boolean isAllowed(String clientIp) {
        Instant now = Instant.now(clock);
        Instant windowStart = now.minus(windowSize);

        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientIp, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            cleanupExpiredEntries(timestamps, windowStart);

            if (timestamps.size() >= maxRequests) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Calculates the number of seconds the caller must wait before the oldest
     * recorded request exits the window, opening a slot.
     *
     * Returns {@code 0} if no entry exists for the given IP.
     *
     * @param clientIp the resolved client IP address
     * @return seconds until the oldest request expires, or 0 if no data exists
     */
    public long getRetryAfterSeconds(String clientIp) {
        Deque<Instant> timestamps = requestLog.get(clientIp);
        if (timestamps == null) {
            return 0L;
        }

        synchronized (timestamps) {
            if (timestamps.isEmpty()) {
                return 0L;
            }

            Instant oldest = timestamps.peekFirst();
            if (oldest == null) {
                return 0L;
            }

            Instant expiry = oldest.plus(windowSize);
            Instant now = Instant.now(clock);
            long secondsRemaining = Duration.between(now, expiry).getSeconds();
            return Math.max(1L, secondsRemaining);
        }
    }

    /**
     * Evicts all IPs from the request log whose entire deque has expired.
     * Called by the scheduler every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanupExpiredEntries() {
        Instant windowStart = Instant.now(clock).minus(windowSize);

        Iterator<Map.Entry<String, Deque<Instant>>> iterator = requestLog.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Deque<Instant>> entry = iterator.next();
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                cleanupExpiredEntries(timestamps, windowStart);
                if (timestamps.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Removes timestamps older than {@code windowStart} from the front of the
     * deque. Assumes the caller holds the monitor on {@code timestamps}.
     */
    private void cleanupExpiredEntries(Deque<Instant> timestamps, Instant windowStart) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
    }

    /**
     * Package-private setter for test Clock injection after construction.
     * Used in unit tests to advance time without Thread.sleep().
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
