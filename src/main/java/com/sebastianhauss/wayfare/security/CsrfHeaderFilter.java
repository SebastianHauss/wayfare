package com.sebastianhauss.wayfare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// The auth cookies must be SameSite=None to work across the frontend/backend
// domain split, which disables the browser's built-in same-site CSRF defense.
// A cross-site page can't attach custom headers without triggering a CORS
// preflight, and our CORS config only allows the real frontend origin, so
// requiring this header on mutating requests stands in for that defense.
@Component
public class CsrfHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_HEADER_VALUE = "WayfareApp";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean anonymousShorten = "POST".equals(request.getMethod()) && "/api/shorten".equals(request.getRequestURI());
        if (MUTATING_METHODS.contains(request.getMethod()) && !anonymousShorten
                && !CSRF_HEADER_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing CSRF header");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
