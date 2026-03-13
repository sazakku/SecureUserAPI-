package com.secureuser.secureuserapi.application.dto;

import java.time.Instant;

public record ApiResponse<T>(
        T data,
        String message,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(data, message, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "OK");
    }
}
