package com.zaplink.dto;

import jakarta.validation.constraints.NotNull;

public record AdminDisableLinkRequest(
        @NotNull(message = "Field 'disabled' is required and must be a boolean")
        Boolean disabled
) {}
