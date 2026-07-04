package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.DeleteAccountRequest;
import com.sebastianhauss.wayfare.dto.LoginRequest;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.dto.MessageResponse;
import com.sebastianhauss.wayfare.dto.RefreshRequest;
import com.sebastianhauss.wayfare.dto.RegisterRequest;
import com.sebastianhauss.wayfare.dto.ResendVerificationRequest;
import com.sebastianhauss.wayfare.dto.VerifyEmailRequest;
import com.sebastianhauss.wayfare.exception.AccountDeletedException;
import com.sebastianhauss.wayfare.exception.EmailAlreadyInUseException;
import com.sebastianhauss.wayfare.exception.EmailNotVerifiedException;
import com.sebastianhauss.wayfare.exception.InvalidCredentialsException;
import com.sebastianhauss.wayfare.exception.InvalidRefreshTokenException;
import com.sebastianhauss.wayfare.exception.InvalidVerificationTokenException;
import com.sebastianhauss.wayfare.exception.ReactivationNotAllowedException;
import com.sebastianhauss.wayfare.exception.UserNotFoundException;
import com.sebastianhauss.wayfare.model.RefreshToken;
import com.sebastianhauss.wayfare.model.User;
import com.sebastianhauss.wayfare.repository.RefreshTokenRepository;
import com.sebastianhauss.wayfare.repository.UserRepository;
import com.sebastianhauss.wayfare.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, emailService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_throws_whenEmailAlreadyInUse() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("user@example.com", "password123")))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_hashesPasswordAndSendsVerificationEmail_withoutIssuingTokens() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-verification-token");

        MessageResponse response = authService.register(new RegisterRequest("user@example.com", "password123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getVerificationToken()).isEqualTo("raw-verification-token");
        assertThat(saved.getVerificationTokenExpiresAt()).isAfter(Instant.now());

        assertThat(response.message()).isNotBlank();
        verify(emailService).sendVerificationEmail("user@example.com", "raw-verification-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_throws_whenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throws_whenPasswordDoesNotMatch() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throws_whenAccountDeleted() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setDeletedAt(Instant.now().minusSeconds(60));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(AccountDeletedException.class);
    }

    @Test
    void login_throws_whenEmailNotVerified() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setEmailVerified(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_issuesTokens_whenCredentialsValid() {
        User user = new User();
        user.setId(3L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void refresh_throws_whenTokenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_throws_whenTokenExpired() {
        RefreshToken expired = new RefreshToken();
        expired.setUserId(1L);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_throws_whenAccountDeleted() {
        RefreshToken existing = new RefreshToken();
        existing.setUserId(9L);
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.of(existing));

        User user = new User();
        user.setId(9L);
        user.setDeletedAt(Instant.now().minusSeconds(60));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(existing.isRevoked()).isTrue();
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refresh_rotatesToken_andIssuesNewPair() {
        RefreshToken existing = new RefreshToken();
        existing.setUserId(9L);
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        when(jwtService.hashToken("raw-old-token")).thenReturn("hashed-old-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-old-token")).thenReturn(Optional.of(existing));

        User user = new User();
        user.setId(9L);
        user.setEmail("user@example.com");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-new-token");
        when(jwtService.hashToken("raw-new-token")).thenReturn("hashed-new-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.refresh(new RefreshRequest("raw-old-token"));

        assertThat(existing.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-new-token");
    }

    @Test
    void logout_revokesMatchingToken() {
        RefreshToken existing = new RefreshToken();
        existing.setUserId(1L);
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.of(existing));

        authService.logout(new RefreshRequest("raw-token"));

        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void logout_isNoop_whenTokenAlreadyRevokedOrMissing() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.empty());

        authService.logout(new RefreshRequest("raw-token"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void getCurrentUser_returnsMappedUser_forAuthenticatedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));

        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        MeResponse response = authService.getCurrentUser();

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    void getCurrentUser_throws_whenUserNoLongerExists() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteAccount_throws_whenUserNoLongerExists() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.deleteAccount(new DeleteAccountRequest("password123")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteAccount_throws_whenAlreadyDeleted() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        User user = new User();
        user.setId(7L);
        user.setDeletedAt(Instant.now());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.deleteAccount(new DeleteAccountRequest("password123")))
                .isInstanceOf(AccountDeletedException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void deleteAccount_throws_whenPasswordIncorrect() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hashed");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.deleteAccount(new DeleteAccountRequest("wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getDeletedAt()).isNull();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void deleteAccount_softDeletesUserAndRevokesAllTokens_whenPasswordCorrect() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hashed");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        authService.deleteAccount(new DeleteAccountRequest("password123"));

        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(7L);
    }

    @Test
    void reactivate_throws_whenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reactivate(new LoginRequest("missing@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reactivate_throws_whenPasswordIncorrect() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setDeletedAt(Instant.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.reactivate(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reactivate_throws_whenAccountIsNotDeleted() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(ReactivationNotAllowedException.class);
    }

    @Test
    void reactivate_throws_whenPastGracePeriod() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setDeletedAt(Instant.now().minus(AccountPurgeService.GRACE_PERIOD_DAYS + 1, ChronoUnit.DAYS));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(ReactivationNotAllowedException.class);
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void reactivate_clearsDeletedAtAndIssuesTokens_whenWithinGracePeriod() {
        User user = new User();
        user.setId(11L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setDeletedAt(Instant.now().minus(AccountPurgeService.GRACE_PERIOD_DAYS - 1, ChronoUnit.DAYS));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.reactivate(new LoginRequest("user@example.com", "password123"));

        assertThat(user.getDeletedAt()).isNull();
        verify(userRepository).save(user);
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void verifyEmail_throws_whenTokenNotFound() {
        when(userRepository.findByVerificationToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("bad-token")))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmail_throws_whenTokenExpired() {
        User user = new User();
        user.setId(5L);
        user.setVerificationToken("expired-token");
        user.setVerificationTokenExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByVerificationToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("expired-token")))
                .isInstanceOf(InvalidVerificationTokenException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_marksVerifiedAndIssuesTokens_whenTokenValid() {
        User user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setVerificationToken("good-token");
        user.setVerificationTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(userRepository.findByVerificationToken("good-token")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.verifyEmail(new VerifyEmailRequest("good-token"));

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
        assertThat(user.getVerificationTokenExpiresAt()).isNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void resendVerification_isNoop_whenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        authService.resendVerification(new ResendVerificationRequest("missing@example.com"));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_isNoop_whenAlreadyVerified() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest("user@example.com"));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_isNoop_whenAccountDeleted() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(false);
        user.setDeletedAt(Instant.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest("user@example.com"));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_issuesNewTokenAndSendsEmail_whenUnverifiedAndActive() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(false);
        user.setVerificationToken("old-token");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("new-token");

        authService.resendVerification(new ResendVerificationRequest("user@example.com"));

        assertThat(user.getVerificationToken()).isEqualTo("new-token");
        assertThat(user.getVerificationTokenExpiresAt()).isAfter(Instant.now());
        verify(userRepository).save(user);
        verify(emailService).sendVerificationEmail("user@example.com", "new-token");
    }
}
