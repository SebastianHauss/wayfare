package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.ClickMetadata;
import com.sebastianhauss.wayfare.dto.LinkResponse;
import com.sebastianhauss.wayfare.dto.LinkStatsResponse;
import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.exception.InvalidUrlException;
import com.sebastianhauss.wayfare.exception.LinkExpiredException;
import com.sebastianhauss.wayfare.exception.ShortenCodeNotFoundException;
import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.repository.ClickEventRepository;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import com.sebastianhauss.wayfare.repository.projection.LabelCount;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortenUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int STATS_WINDOW_DAYS = 30;

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

    public String getUrl(String code) {
        return getUrl(code, ClickMetadata.empty());
    }

    @Transactional
    public String getUrl(String code, ClickMetadata metadata) {
        String hit = redisTemplate.opsForValue().get(code);
        if (hit != null) {
            log.debug("Cache hit for code={}", code);
            registerClick(code, metadata);
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
            registerClick(code, metadata);
            return originalUrl;
        } else {
            log.warn("Short code not found: {}", code);
            throw new ShortenCodeNotFoundException("Short code not found");
        }
    }

    private void registerClick(String code, ClickMetadata metadata) {
        shortUrlRepository.incrementClickCount(code);
        clickEventRepository.recordClick(
                code, metadata.referrerDomain(), metadata.country(), metadata.deviceType(), metadata.browser());
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> getMyLinks() {
        Long userId = currentUserId();
        return shortUrlRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toLinkResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkStatsResponse getLinkStats(String code) {
        Long userId = currentUserId();
        ShortUrl link = shortUrlRepository.findByShortCodeAndUserId(code, userId)
                .orElseThrow(() -> new ShortenCodeNotFoundException("Short code not found"));
        Long linkId = link.getId();
        Instant since = Instant.now().minus(Duration.ofDays(STATS_WINDOW_DAYS));

        List<LinkStatsResponse.DailyCount> clicksByDay = clickEventRepository.clicksByDay(linkId, since).stream()
                .map(d -> new LinkStatsResponse.DailyCount(d.getDay(), d.getCount()))
                .toList();
        long totalClicks = link.getClickCount() == null ? 0L : link.getClickCount();

        return new LinkStatsResponse(
                totalClicks,
                clicksByDay,
                toBuckets(clickEventRepository.topReferrers(linkId)),
                toBuckets(clickEventRepository.topCountries(linkId)),
                toBuckets(clickEventRepository.deviceBreakdown(linkId)));
    }

    private List<LinkStatsResponse.Bucket> toBuckets(List<LabelCount> rows) {
        return rows.stream()
                .map(r -> new LinkStatsResponse.Bucket(r.getLabel(), r.getCount()))
                .toList();
    }

    @Transactional
    public void deleteLink(String code) {
        Long userId = currentUserId();
        ShortUrl shortUrl = shortUrlRepository.findByShortCodeAndUserId(code, userId)
                .orElseThrow(() -> new ShortenCodeNotFoundException("Short code not found"));
        shortUrlRepository.delete(shortUrl);
        redisTemplate.delete(code);
        log.info("Deleted short URL: {}", code);
    }

    private LinkResponse toLinkResponse(ShortUrl shortUrl) {
        return new LinkResponse(
                shortUrl.getShortCode(),
                baseUrl + "/" + shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getClickCount(),
                shortUrl.getExpiresAt(),
                shortUrl.getMaxClicks()
        );
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
