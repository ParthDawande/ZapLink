package com.zaplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLinkRequest {

    @Schema(description = "The URL to shorten — must start with http:// or https://",
            example = "https://example.com/some/very/long/article-url")
    @NotBlank(message = "Long URL is required")
    @Size(max = 2048, message = "URL exceeds maximum length of 2048 characters")
    private String longUrl;

    @Schema(description = "Optional expiration as ISO-8601 datetime — must be in the future. " +
                          "Omit or set to null for a link that never expires.",
            example = "2025-12-31T23:59:59",
            nullable = true)
    private LocalDateTime expiresAt;
}
