package com.sebastianhauss.wayfare.dto;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl
) {
}
