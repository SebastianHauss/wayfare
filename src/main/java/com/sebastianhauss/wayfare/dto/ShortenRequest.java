package com.sebastianhauss.wayfare.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record ShortenRequest(
        @NotBlank @Pattern(regexp = "^https?://.+") String url,
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "must be 3-32 characters using letters, numbers, hyphens or underscores")
        String alias,
        @Future Instant expiresAt,
        @Positive Long maxClicks
) {
    public ShortenRequest(String url) {
        this(url, null, null, null);
    }
}
