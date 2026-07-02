package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.config.RedisConfig;
import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.exception.ShortenCodeNotFoundException;
import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import com.sebastianhauss.wayfare.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(request.url());
        ShortUrl saved = shortUrlRepository.save(shortUrl);
        String shortCode = Base62Encoder.encode(saved.getId());
        saved.setShortCode(shortCode);
        ShortUrl updated = shortUrlRepository.save(saved);
        String shortUrlString = baseUrl + "/" + updated.getShortCode();
        return new ShortenResponse(updated.getShortCode(), shortUrlString, updated.getOriginalUrl());
    }

    @Transactional
    public String getUrl(String code) {
       String hit = redisTemplate.opsForValue().get(code);
       if (hit != null) {
           shortUrlRepository.incrementClickCount(code);
           return hit;
       }
       Optional<ShortUrl> shortUrl = shortUrlRepository.findByShortCode(code);
       if (shortUrl.isPresent()) {
           String originalUrl = shortUrl.get().getOriginalUrl();
           redisTemplate.opsForValue().set(code, originalUrl, Duration.ofHours(24));
           shortUrlRepository.incrementClickCount(code);
           return originalUrl;
       } else {
           throw new ShortenCodeNotFoundException("Short code not found");
       }
    }

    @Transactional
    public String getShortUrl(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortenCodeNotFoundException("Short code not found"));
        return baseUrl + "/" + shortUrl.getShortCode();
    }
}
