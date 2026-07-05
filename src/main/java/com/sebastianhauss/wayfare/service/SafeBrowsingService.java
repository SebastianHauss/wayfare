package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.exception.UnsafeUrlException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Screens destination URLs against the Google Safe Browsing Lookup API so we
// don't hand out short links to known malware/phishing pages (which would also
// risk getting our own domain blocklisted).
@Service
@RequiredArgsConstructor
@Slf4j
public class SafeBrowsingService {

    private static final List<String> THREAT_TYPES =
            List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

    private final RestClient.Builder restClientBuilder;

    @Value("${app.safe-browsing-api-key:}")
    private String apiKey;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = restClientBuilder.baseUrl("https://safebrowsing.googleapis.com").build();
    }

    // Throws UnsafeUrlException if Google flags the URL. When no API key is set
    // (local dev, tests) the check is skipped. On any API error we fail open — a
    // shortener shouldn't stop working because a reputation feed is unreachable.
    public void verifySafe(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        Map<String, Object> body = Map.of(
                "client", Map.of("clientId", "wayfare", "clientVersion", "1.0"),
                "threatInfo", Map.of(
                        "threatTypes", THREAT_TYPES,
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", List.of(Map.of("url", url))));

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/v4/threatMatches:find?key={key}", apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Safe Browsing lookup failed, allowing URL: {}", e.getMessage());
            return;
        }

        // An empty response body means no threat matched; a "matches" array means
        // at least one threat type hit.
        if (response != null && response.containsKey("matches")) {
            log.warn("Blocked unsafe URL flagged by Safe Browsing: {}", url);
            throw new UnsafeUrlException("This URL was flagged as unsafe and can't be shortened");
        }
    }
}
