package com.sebastianhauss.wayfare.security;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.exception.ReactivationNotAllowedException;
import com.sebastianhauss.wayfare.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuth2EmailResolver emailResolver;
    private final AuthCookieService authCookieService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String email = emailResolver.resolve(oauthToken);

        if (email == null || email.isBlank()) {
            log.warn("{} sign-in returned no email attribute", provider);
            response.sendRedirect(callbackUrl("error=" + encode("Your " + provider + " account did not share an email address")));
            return;
        }

        try {
            AuthResponse tokens = authService.loginWithOAuth(email, provider);
            authCookieService.setAuthCookies(response, tokens.accessToken(), tokens.refreshToken());
            response.sendRedirect(frontendUrl);
        } catch (ReactivationNotAllowedException e) {
            response.sendRedirect(callbackUrl("error=" + encode(e.getMessage())));
        }
    }

    private String callbackUrl(String fragment) {
        return frontendUrl + "/auth/callback#" + fragment;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
