package com.zaplink.dto;

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

    @NotBlank(message = "Long URL is required")
    @Size(max = 2048, message = "URL exceeds maximum length of 2048 characters")
    private String longUrl;

    private LocalDateTime expiresAt;
}
