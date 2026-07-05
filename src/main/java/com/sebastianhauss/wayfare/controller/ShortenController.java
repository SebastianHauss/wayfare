package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.ClickMetadata;
import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import com.sebastianhauss.wayfare.util.QrCodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    public ResponseEntity<Void> get(@PathVariable String code, HttpServletRequest request) {
        ClickMetadata metadata = ClickMetadata.from(
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                // Set by the Cloudflare Worker from request.cf.country; absent in local dev.
                request.getHeader("X-Client-Country"));
        String originalUrl = shortenUrlService.getUrl(code, metadata);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @GetMapping("/{code}/qr")
    public ResponseEntity<byte[]> getQrCode(@PathVariable String code) {
        String shortUrl = shortenUrlService.getShortUrl(code);
        byte[] png = QrCodeGenerator.generate(shortUrl, 300);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
