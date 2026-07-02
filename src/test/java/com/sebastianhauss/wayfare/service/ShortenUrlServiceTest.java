package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
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
        ShortUrl saved = new ShortUrl();
        saved.setId(5L);
        saved.setOriginalUrl("https://example.com/some/long/path");
        when(shortUrlRepository.save(any(ShortUrl.class))).thenReturn(saved);

        ShortenResponse response = shortenUrlService.shorten(new ShortenRequest("https://example.com/some/long/path"));

        assertThat(response.shortCode()).isEqualTo("5");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/5");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/some/long/path");
        verify(shortUrlRepository, times(2)).save(any(ShortUrl.class));
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
    void getUrl_throwsNotFound_whenCodeMissingEverywhere() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("missing")).thenReturn(null);
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortenUrlService.getUrl("missing"))
                .isInstanceOf(ShortenCodeNotFoundException.class);
    }
}
