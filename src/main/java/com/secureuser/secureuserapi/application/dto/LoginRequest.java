package com.secureuser.secureuserapi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "must contain only letters, digits, or underscores")
        String username,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
