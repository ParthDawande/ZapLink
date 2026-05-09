package com.zaplink.dto;

import java.time.LocalDateTime;

public record AdminLinkResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String longUrl,
        LocalDateTime expiresAt,
        Boolean isActive,
        String disabledReason,
        LocalDateTime deletedAt,
        String status,
        Long clickCount,
        AdminLinkOwner owner,
        LocalDateTime createdAt
) {}
