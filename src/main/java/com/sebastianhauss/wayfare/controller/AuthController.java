package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.security.AuthCookieService;
import com.sebastianhauss.wayfare.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthResponse tokens = authService.refresh(refreshToken);
        authCookieService.setAuthCookies(response, tokens.accessToken(), tokens.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(HttpServletResponse response) {
        authService.deleteAccount();
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }
}
