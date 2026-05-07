package com.zaplink.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                 message = "Username can only contain letters, digits, and underscores")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least 1 letter and 1 digit")
        String password
) {}
