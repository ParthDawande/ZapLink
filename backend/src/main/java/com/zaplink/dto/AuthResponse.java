package com.zaplink.dto;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String email,
        String role,
        Long expiresIn
) {}
