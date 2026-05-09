package com.zaplink.dto;

import com.zaplink.model.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        Role role,
        Boolean isActive,
        Long linkCount,
        LocalDateTime createdAt
) {}
