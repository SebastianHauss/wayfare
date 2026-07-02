package com.sebastianhauss.wayfare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShortenRequest(@NotBlank @Pattern(regexp = "^https?://.+") String url) {
}
