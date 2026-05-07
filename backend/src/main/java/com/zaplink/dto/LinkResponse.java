package com.zaplink.dto;

import java.time.LocalDateTime;

public record LinkResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String longUrl,
        LocalDateTime expiresAt,
        Boolean isActive,
        LocalDateTime createdAt
) {}
