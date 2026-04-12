package com.secureuser.secureuserapi.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Provides a UTC system clock as a Spring bean so that services depending
     * on the current time (e.g. {@code RateLimiterService}) can be tested by
     * injecting a {@code Clock.fixed()} instance.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
