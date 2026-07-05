package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.LoginRequest;
import com.sebastianhauss.wayfare.dto.ResetPasswordRequest;
import com.sebastianhauss.wayfare.model.User;
import com.sebastianhauss.wayfare.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void resetPassword_persistsNewPasswordAndAllowsLogin() {
        User user = new User();
        user.setEmail("reset-password-integration@example.com");
        user.setPasswordHash(passwordEncoder.encode("oldpassword123"));
        user.setEmailVerified(true);
        user.setPasswordResetToken("integration-reset-token");
        user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
        userRepository.saveAndFlush(user);

        authService.resetPassword(new ResetPasswordRequest("integration-reset-token", "newpassword123"));

        User saved = userRepository.findByEmail("reset-password-integration@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("newpassword123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getPasswordResetToken()).isNull();
        assertThat(authService.login(new LoginRequest("reset-password-integration@example.com", "newpassword123")).user().email())
                .isEqualTo("reset-password-integration@example.com");
    }
}
