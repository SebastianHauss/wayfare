package com.sebastianhauss.wayfare.dto;

import java.time.Instant;

public record LinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Long clickCount,
        Instant expiresAt,
        Long maxClicks
) {
}
