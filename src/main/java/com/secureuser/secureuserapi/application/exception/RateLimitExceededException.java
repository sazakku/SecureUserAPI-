package com.secureuser.secureuserapi.application.exception;

/**
 * Thrown when a client exceeds the configured rate limit for a given endpoint.
 *
 * Primary enforcement path is {@code RateLimitFilter}, which writes the 429
 * response directly. This exception exists as a safety net for the
 * {@code GlobalExceptionHandler} in case future code paths throw it from
 * within the controller or service layer.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
