package com.sebastianhauss.wayfare.dto;

import java.time.Instant;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant expiresAt,
        Long maxClicks
) {
}
