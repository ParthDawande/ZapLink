package com.zaplink.dto;

import java.time.LocalDateTime;

public record LinkResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String longUrl,
        LocalDateTime expiresAt,
        Boolean isActive,
        String disabledReason,
        String status,
        Long clickCount,
        LocalDateTime createdAt
) {
    public static String computeStatus(Boolean isActive, LocalDateTime expiresAt) {
        if (Boolean.FALSE.equals(isActive)) return "disabled";
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return "expired";
        return "active";
    }
}
