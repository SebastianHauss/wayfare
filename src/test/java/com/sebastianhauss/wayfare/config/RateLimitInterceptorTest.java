package com.sebastianhauss.wayfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void preHandle_allowsRequest_underLimit() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void preHandle_setsExpiry_onFirstRequestInWindow() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        interceptor.preHandle(request, response, new Object());

        verify(redisTemplate).expire(eq("ratelimit:127.0.0.1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void preHandle_doesNotResetExpiry_onSubsequentRequests() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        interceptor.preHandle(request, response, new Object());

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void preHandle_blocksRequest_overLimit() throws Exception {
        StringWriter responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(valueOperations.increment(anyString())).thenReturn(11L);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(responseBody.toString()).contains("Rate limit exceeded");
    }

    @Test
    void preHandle_prefersForwardedForHeader_overRemoteAddr() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        interceptor.preHandle(request, response, new Object());

        verify(valueOperations).increment("ratelimit:203.0.113.5");
    }
}
