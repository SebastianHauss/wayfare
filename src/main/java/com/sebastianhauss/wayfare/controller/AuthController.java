package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResult;
import com.sebastianhauss.wayfare.dto.DeleteAccountRequest;
import com.sebastianhauss.wayfare.dto.LoginRequest;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.dto.MessageResponse;
import com.sebastianhauss.wayfare.dto.RegisterRequest;
import com.sebastianhauss.wayfare.dto.ResendVerificationRequest;
import com.sebastianhauss.wayfare.dto.VerifyEmailRequest;
import com.sebastianhauss.wayfare.security.AuthCookieService;
import com.sebastianhauss.wayfare.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MeResponse> verifyEmail(@RequestBody @Valid VerifyEmailRequest request, HttpServletResponse response) {
        return authenticated(authService.verifyEmail(request), response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(new MessageResponse("If an account exists for that email, a verification link has been sent."));
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        return authenticated(authService.login(request), response);
    }

    @PostMapping("/reactivate")
    public ResponseEntity<MeResponse> reactivate(@RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        return authenticated(authService.reactivate(request), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        var tokens = authService.refresh(refreshToken);
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
    public ResponseEntity<Void> deleteAccount(@RequestBody @Valid DeleteAccountRequest request, HttpServletResponse response) {
        authService.deleteAccount(request);
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<MeResponse> authenticated(AuthResult result, HttpServletResponse response) {
        authCookieService.setAuthCookies(response, result.tokens().accessToken(), result.tokens().refreshToken());
        return ResponseEntity.ok(result.user());
    }
}
