package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.ShortenRequest;
import com.sebastianhauss.wayfare.dto.ShortenResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortenControllerTest {

    @Mock
    private ShortenUrlService shortenUrlService;

    private ShortenController shortenController;

    @BeforeEach
    void setUp() {
        shortenController = new ShortenController(shortenUrlService);
    }

    @Test
    void post_returnsCreatedWithShortenResponse() {
        ShortenRequest request = new ShortenRequest("https://example.com");
        ShortenResponse response = new ShortenResponse("abc", "http://localhost:8080/abc", "https://example.com");
        when(shortenUrlService.shorten(request)).thenReturn(response);

        ResponseEntity<ShortenResponse> result = shortenController.post(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void get_redirectsToOriginalUrl() {
        when(shortenUrlService.getUrl(eq("abc"), any())).thenReturn("https://example.com");

        ResponseEntity<Void> result = shortenController.get("abc", new MockHttpServletRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(result.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com"));
    }
}
