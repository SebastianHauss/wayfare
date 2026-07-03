package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.LinkResponse;
import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.exception.InvalidUrlException;
import com.sebastianhauss.wayfare.exception.LinkExpiredException;
import com.sebastianhauss.wayfare.exception.ShortenCodeNotFoundException;
import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
    void shorten_throwsInvalidUrl_whenUrlPointsBackToThisService() {
        assertThatThrownBy(() -> shortenUrlService.shorten(new ShortenRequest("http://localhost:8080/abc123")))
                .isInstanceOf(InvalidUrlException.class);

        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void shorten_leavesUserIdNull_whenAnonymous() {
        ShortUrl saved = new ShortUrl();
        saved.setId(5L);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenReturn(saved);

        shortenUrlService.shorten(new ShortenRequest("https://example.com"));

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(shortUrlRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserId()).isNull();
    }

    @Test
    void shorten_setsUserId_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, java.util.List.of()));

        ShortUrl saved = new ShortUrl();
        saved.setId(5L);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenReturn(saved);

        shortenUrlService.shorten(new ShortenRequest("https://example.com"));

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(shortUrlRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserId()).isEqualTo(42L);
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

    @Test
    void getMyLinks_returnsMappedLinksForCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, java.util.List.of()));

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setShortCode("abc");
        shortUrl.setOriginalUrl("https://example.com");
        shortUrl.setClickCount(3L);
        when(shortUrlRepository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(shortUrl));

        List<LinkResponse> result = shortenUrlService.getMyLinks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).shortCode()).isEqualTo("abc");
        assertThat(result.get(0).shortUrl()).isEqualTo("http://localhost:8080/abc");
        assertThat(result.get(0).originalUrl()).isEqualTo("https://example.com");
        assertThat(result.get(0).clickCount()).isEqualTo(3L);
    }

    @Test
    void deleteLink_deletesLinkAndEvictsCache_whenOwnedByCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, java.util.List.of()));

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setShortCode("abc");
        when(shortUrlRepository.findByShortCodeAndUserId("abc", 42L)).thenReturn(Optional.of(shortUrl));

        shortenUrlService.deleteLink("abc");

        verify(shortUrlRepository).delete(shortUrl);
        verify(redisTemplate).delete("abc");
    }

    @Test
    void deleteLink_throwsNotFound_whenNotOwnedByCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, java.util.List.of()));

        when(shortUrlRepository.findByShortCodeAndUserId("abc", 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortenUrlService.deleteLink("abc"))
                .isInstanceOf(ShortenCodeNotFoundException.class);
        verify(shortUrlRepository, never()).delete(any());
    }
}
