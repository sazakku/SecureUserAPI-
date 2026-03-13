package com.secureuser.secureuserapi.application.dto;

public record AuthResponse(
        String token,
        String type,
        String username
) {
    public static AuthResponse of(String token, String username) {
        return new AuthResponse(token, "Bearer", username);
    }
}
