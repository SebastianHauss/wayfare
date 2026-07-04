package com.sebastianhauss.wayfare.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    private final JwtService jwtService;

    // Frontend and backend live on different domains, so these cookies must be
    // SameSite=None to be sent on cross-origin fetches; CsrfHeaderFilter makes
    // up for None disabling the browser's usual same-site CSRF protection.
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, "/", jwtService.getAccessTokenExpirationSeconds());
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH, jwtService.getRefreshTokenExpirationSeconds());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", "/", 0);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
