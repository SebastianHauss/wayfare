package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.exception.InvalidUrlException;
import com.sebastianhauss.wayfare.exception.LinkExpiredException;
import com.sebastianhauss.wayfare.exception.ShortenCodeNotFoundException;
import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import com.sebastianhauss.wayfare.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortenUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        if (request.url().startsWith(baseUrl)) {
            throw new InvalidUrlException("Cannot shorten a URL that points back to this service");
        }
        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(request.url());
        shortUrl.setExpiresAt(request.expiresAt());
        shortUrl.setMaxClicks(request.maxClicks());
        shortUrl.setUserId(currentUserId());
        ShortUrl saved = shortUrlRepository.save(shortUrl);
        String shortCode = Base62Encoder.encode(saved.getId());
        saved.setShortCode(shortCode);
        ShortUrl updated = shortUrlRepository.save(saved);
        String shortUrlString = baseUrl + "/" + updated.getShortCode();
        log.info("Created short URL: {} -> {}", updated.getShortCode(), updated.getOriginalUrl());
        return new ShortenResponse(updated.getShortCode(), shortUrlString, updated.getOriginalUrl());
    }

    @Transactional
    public String getUrl(String code) {
        String hit = redisTemplate.opsForValue().get(code);
        if (hit != null) {
            log.debug("Cache hit for code={}", code);
            shortUrlRepository.incrementClickCount(code);
            return hit;
        }
        Optional<ShortUrl> shortUrl = shortUrlRepository.findByShortCode(code);
        if (shortUrl.isPresent()) {
            ShortUrl entity = shortUrl.get();
            if (isExpired(entity)) {
                throw new LinkExpiredException("Link expired");
            }
            log.debug("Cache miss for code={}, fetched from database", code);
            String originalUrl = entity.getOriginalUrl();
            boolean hasExpiration = entity.getExpiresAt() != null || entity.getMaxClicks() != null;
            if (!hasExpiration) {
                redisTemplate.opsForValue().set(code, originalUrl, Duration.ofHours(24));
            }
            shortUrlRepository.incrementClickCount(code);
            return originalUrl;
        } else {
            log.warn("Short code not found: {}", code);
            throw new ShortenCodeNotFoundException("Short code not found");
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Long userId ? userId : null;
    }

    private boolean isExpired(ShortUrl entity) {
        boolean expiredByDate = entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now());
        boolean expiredByClicks = entity.getMaxClicks() != null && entity.getClickCount() >= entity.getMaxClicks();
        return expiredByDate || expiredByClicks;
    }

    @Transactional
    public String getShortUrl(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortenCodeNotFoundException("Short code not found"));
        return baseUrl + "/" + shortUrl.getShortCode();
    }
}
