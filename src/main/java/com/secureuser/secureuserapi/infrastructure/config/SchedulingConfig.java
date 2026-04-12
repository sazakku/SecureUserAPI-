package com.secureuser.secureuserapi.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's @Scheduled task execution.
 *
 * Placed in a dedicated configuration class rather than on the main
 * application class to keep scheduling as an opt-in infrastructure concern
 * that can be excluded in test slices via @SpringBootTest(exclude = ...).
 *
 * Required by {@code RateLimiterService#cleanupExpiredEntries()} which runs
 * every 5 minutes to evict fully-expired IP entries from the in-memory store.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
