package com.zaplink.dto;

import jakarta.validation.constraints.NotNull;

public record AdminBanUserRequest(
        @NotNull(message = "Field 'banned' is required and must be a boolean")
        Boolean banned
) {}
