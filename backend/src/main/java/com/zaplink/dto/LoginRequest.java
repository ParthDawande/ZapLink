package com.zaplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "Email address used during registration", example = "john@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        String email,

        @Schema(description = "Account password", example = "securePass123")
        @NotBlank(message = "Password is required")
        String password
) {}
