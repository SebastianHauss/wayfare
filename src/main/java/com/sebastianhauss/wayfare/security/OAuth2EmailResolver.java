package com.sebastianhauss.wayfare.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2EmailResolver {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient = RestClient.create();

    public String resolve(OAuth2AuthenticationToken oauthToken) {
        String email = oauthToken.getPrincipal().getAttribute("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        if ("github".equals(oauthToken.getAuthorizedClientRegistrationId())) {
            return githubPrimaryEmail(oauthToken).orElse(null);
        }
        return null;
    }

    private Optional<String> githubPrimaryEmail(OAuth2AuthenticationToken oauthToken) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("github", oauthToken.getName());
        if (client == null) {
            return Optional.empty();
        }
        try {
            GitHubEmail[] emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
                    .retrieve()
                    .body(GitHubEmail[].class);
            if (emails == null) {
                return Optional.empty();
            }
            return Arrays.stream(emails)
                    .filter(email -> email.verified() && email.primary())
                    .map(GitHubEmail::email)
                    .findFirst()
                    .or(() -> Arrays.stream(emails)
                            .filter(GitHubEmail::verified)
                            .map(GitHubEmail::email)
                            .findFirst());
        } catch (RuntimeException e) {
            log.warn("GitHub sign-in did not return a usable email", e);
            return Optional.empty();
        }
    }

    private record GitHubEmail(String email, boolean primary, boolean verified) {
    }
}
