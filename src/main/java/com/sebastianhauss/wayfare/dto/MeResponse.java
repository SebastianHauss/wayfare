package com.sebastianhauss.wayfare.dto;

import java.time.Instant;

public record MeResponse(
        Long id,
        String email,
        Instant createdAt
) {
}
