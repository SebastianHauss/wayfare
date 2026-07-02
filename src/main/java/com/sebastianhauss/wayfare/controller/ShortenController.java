package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class ShortenController {

    private final ShortenUrlService shortenUrlService;

    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> post(@RequestBody @Valid ShortenRequest shortenRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shortenUrlService.shorten(shortenRequest));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> get(@PathVariable String code) {
        String originalUrl = shortenUrlService.getUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
