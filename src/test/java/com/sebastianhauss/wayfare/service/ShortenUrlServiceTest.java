package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.exception.InvalidUrlException;
import com.sebastianhauss.wayfare.exception.LinkExpiredException;
import com.sebastianhauss.wayfare.exception.ShortenCodeNotFoundException;
import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortenUrlServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ShortenUrlService shortenUrlService;

    @BeforeEach
    void setUp() {
        shortenUrlService = new ShortenUrlService(shortUrlRepository, redisTemplate);
        ReflectionTestUtils.setField(shortenUrlService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void shorten_encodesIdAndBuildsShortUrl() {
        when(shortUrlRepository.nextId()).thenReturn(5L);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortenResponse response = shortenUrlService.shorten(new ShortenRequest("https://example.com/some/long/path"));

        assertThat(response.shortCode()).isEqualTo("5");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/5");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/some/long/path");
        verify(shortUrlRepository, times(1)).save(any(ShortUrl.class));
    }

    @Test
    void shorten_throwsInvalidUrl_whenUrlPointsBackToThisService() {
        assertThatThrownBy(() -> shortenUrlService.shorten(new ShortenRequest("http://localhost:8080/abc123")))
                .isInstanceOf(InvalidUrlException.class);

        verify(shortUrlRepository, never()).nextId();
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void getUrl_returnsFromCache_onHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn("https://example.com");

        String result = shortenUrlService.getUrl("abc");

        assertThat(result).isEqualTo("https://example.com");
        verify(shortUrlRepository, never()).findByShortCode(any());
        verify(shortUrlRepository).incrementClickCount("abc");
    }

    @Test
    void getUrl_fallsBackToDatabase_onCacheMiss_andRepopulatesCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn(null);

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl("https://example.com");
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        String result = shortenUrlService.getUrl("abc");

        assertThat(result).isEqualTo("https://example.com");
        verify(valueOperations).set(eq("abc"), eq("https://example.com"), eq(Duration.ofHours(24)));
        verify(shortUrlRepository).incrementClickCount("abc");
    }

    @Test
    void getUrl_throwsExpired_whenExpiresAtIsInThePast() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn(null);

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl("https://example.com");
        shortUrl.setExpiresAt(Instant.now().minusSeconds(60));
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> shortenUrlService.getUrl("abc"))
                .isInstanceOf(LinkExpiredException.class);
        verify(shortUrlRepository, never()).incrementClickCount(any());
    }

    @Test
    void getUrl_stillWorks_whenExpiresAtIsInTheFuture() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn(null);

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl("https://example.com");
        shortUrl.setExpiresAt(Instant.now().plusSeconds(60));
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        String result = shortenUrlService.getUrl("abc");

        assertThat(result).isEqualTo("https://example.com");
        verify(shortUrlRepository).incrementClickCount("abc");
    }

    @Test
    void getUrl_throwsExpired_whenMaxClicksReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn(null);

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl("https://example.com");
        shortUrl.setMaxClicks(5L);
        shortUrl.setClickCount(5L);
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> shortenUrlService.getUrl("abc"))
                .isInstanceOf(LinkExpiredException.class);
        verify(shortUrlRepository, never()).incrementClickCount(any());
    }

    @Test
    void getUrl_doesNotCache_whenLinkHasExpirationConfigured() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc")).thenReturn(null);

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl("https://example.com");
        shortUrl.setMaxClicks(5L);
        shortUrl.setClickCount(1L);
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        shortenUrlService.getUrl("abc");

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void getUrl_throwsNotFound_whenCodeMissingEverywhere() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("missing")).thenReturn(null);
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortenUrlService.getUrl("missing"))
                .isInstanceOf(ShortenCodeNotFoundException.class);
    }

    @Test
    void getShortUrl_buildsUrlFromStoredShortCode() {
        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setShortCode("abc");
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        String result = shortenUrlService.getShortUrl("abc");

        assertThat(result).isEqualTo("http://localhost:8080/abc");
    }

    @Test
    void getShortUrl_throwsNotFound_whenCodeMissing() {
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortenUrlService.getShortUrl("missing"))
                .isInstanceOf(ShortenCodeNotFoundException.class);
    }
}
