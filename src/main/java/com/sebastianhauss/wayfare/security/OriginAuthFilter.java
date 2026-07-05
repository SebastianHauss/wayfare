package com.sebastianhauss.wayfare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// The public frontend is a Cloudflare Worker that proxies every request to this
// origin and injects trust-bearing headers (x-client-country, x-forwarded-*)
// server-side. Anyone who reaches the origin directly bypasses the Worker and
// can forge those headers, so we require a shared secret that only the Worker
// knows. It travels on the Worker->origin hop and never reaches the browser.
//
// When app.origin-shared-secret is blank (local dev, tests) the check is a
// no-op, so the backend still runs without the Worker in front of it.
@Component
@Slf4j
public class OriginAuthFilter extends OncePerRequestFilter {

    public static final String ORIGIN_AUTH_HEADER = "X-Origin-Auth";

    private final byte[] expectedSecret;
    private final boolean enabled;

    public OriginAuthFilter(@Value("${app.origin-shared-secret:}") String originSharedSecret) {
        this.enabled = originSharedSecret != null && !originSharedSecret.isBlank();
        this.expectedSecret = enabled ? originSharedSecret.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (enabled && !secretMatches(request.getHeader(ORIGIN_AUTH_HEADER))) {
            // 404 rather than 403 so a direct probe can't distinguish "wrong
            // secret" from "no such host" and confirm this is the real origin.
            log.warn("Rejected request missing valid origin secret: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean secretMatches(String provided) {
        if (provided == null) {
            return false;
        }
        // Constant-time compare so the response timing doesn't leak how many
        // leading bytes of a guessed secret were correct.
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }
}
