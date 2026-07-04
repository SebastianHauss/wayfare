package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.exception.AccountDeletedException;
import com.sebastianhauss.wayfare.exception.InvalidRefreshTokenException;
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
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubTokenIssuing(User user) {
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }

    @Test
    void loginWithOAuth_createsUserWithProvider_whenEmailUnknown() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(2592000));
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.loginWithOAuth("new@example.com", "google");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(userCaptor.getValue().getProvider()).isEqualTo("google");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithOAuth_issuesTokens_forExistingActiveUser() {
        User user = new User();
        user.setId(3L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        stubTokenIssuing(user);

        AuthResponse response = authService.loginWithOAuth("user@example.com", "google");

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(user);
    }

    @Test
    void loginWithOAuth_reactivatesAccount_whenDeletedWithinGracePeriod() {
        User user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setDeletedAt(Instant.now().minus(AccountPurgeService.GRACE_PERIOD_DAYS - 1, ChronoUnit.DAYS));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        stubTokenIssuing(user);

        AuthResponse response = authService.loginWithOAuth("user@example.com", "google");

        assertThat(user.getDeletedAt()).isNull();
        verify(userRepository).save(user);
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginWithOAuth_throws_whenDeletedPastGracePeriod() {
        User user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setDeletedAt(Instant.now().minus(AccountPurgeService.GRACE_PERIOD_DAYS + 1, ChronoUnit.DAYS));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithOAuth("user@example.com", "google"))
                .isInstanceOf(ReactivationNotAllowedException.class);
        assertThat(user.getDeletedAt()).isNotNull();
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refresh_throws_whenTokenMissing() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authService.refresh(" "))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_throws_whenTokenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_throws_whenTokenExpired() {
        RefreshToken expired = new RefreshToken();
        expired.setUserId(1L);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
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

        assertThatThrownBy(() -> authService.refresh("raw-token"))
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

        AuthResponse response = authService.refresh("raw-old-token");

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

        authService.logout("raw-token");

        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void logout_isNoop_whenTokenIsNull() {
        authService.logout(null);

        verify(refreshTokenRepository, never()).save(any());
        verify(jwtService, never()).hashToken(any());
    }

    @Test
    void logout_isNoop_whenTokenAlreadyRevokedOrMissing() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse("hashed-token")).thenReturn(Optional.empty());

        authService.logout("raw-token");

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

        assertThatThrownBy(() -> authService.deleteAccount())
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

        assertThatThrownBy(() -> authService.deleteAccount())
                .isInstanceOf(AccountDeletedException.class);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void deleteAccount_softDeletesUserAndRevokesAllTokens() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        User user = new User();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        authService.deleteAccount();

        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(7L);
    }
}
