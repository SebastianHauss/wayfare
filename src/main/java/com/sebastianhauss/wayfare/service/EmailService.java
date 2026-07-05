package com.sebastianhauss.wayfare.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.mail-from}")
    private String mailFrom;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
    }

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String text = "Click the link below to verify your email address:\n\n" + link
                + "\n\nThis link expires in 24 hours. If you didn't sign up for Wayfare, you can ignore this email.";
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .body(Map.of(
                            "from", mailFrom,
                            "to", List.of(toEmail),
                            "subject", "Verify your Wayfare account",
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent verification email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String text = "We received a request to reset your Wayfare password. Click the link below to choose a new one:\n\n" + link
                + "\n\nThis link expires in 1 hour. If you didn't request a password reset, you can safely ignore this email.";
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .body(Map.of(
                            "from", mailFrom,
                            "to", List.of(toEmail),
                            "subject", "Reset your Wayfare password",
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent password reset email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
