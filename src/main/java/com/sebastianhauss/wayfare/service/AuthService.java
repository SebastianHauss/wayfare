package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.DeleteAccountRequest;
import com.sebastianhauss.wayfare.dto.LoginRequest;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.dto.RefreshRequest;
import com.sebastianhauss.wayfare.dto.RegisterRequest;
import com.sebastianhauss.wayfare.exception.AccountDeletedException;
import com.sebastianhauss.wayfare.exception.EmailAlreadyInUseException;
import com.sebastianhauss.wayfare.exception.InvalidCredentialsException;
import com.sebastianhauss.wayfare.exception.InvalidRefreshTokenException;
import com.sebastianhauss.wayfare.exception.ReactivationNotAllowedException;
import com.sebastianhauss.wayfare.exception.UserNotFoundException;
import com.sebastianhauss.wayfare.model.RefreshToken;
import com.sebastianhauss.wayfare.model.User;
import com.sebastianhauss.wayfare.repository.RefreshTokenRepository;
import com.sebastianhauss.wayfare.repository.UserRepository;
import com.sebastianhauss.wayfare.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyInUseException("Email already in use: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        log.info("Registered user: {}", saved.getEmail());
        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (user.getDeletedAt() != null) {
            throw new AccountDeletedException("This account has been deleted");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken existing = validRefreshToken(request.refreshToken());
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token no longer valid"));
        if (user.getDeletedAt() != null) {
            throw new InvalidRefreshTokenException("Refresh token no longer valid");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser() {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new UserNotFoundException("User no longer exists"));
        return new MeResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    @Transactional
    public AuthResponse reactivate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (user.getDeletedAt() == null) {
            throw new ReactivationNotAllowedException("Account is not deleted");
        }
        Instant cutoff = Instant.now().minus(AccountPurgeService.GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        if (user.getDeletedAt().isBefore(cutoff)) {
            throw new ReactivationNotAllowedException("This account was permanently deleted and can no longer be reactivated");
        }
        user.setDeletedAt(null);
        userRepository.save(user);
        log.info("Reactivated user account: {}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new UserNotFoundException("User no longer exists"));
        if (user.getDeletedAt() != null) {
            throw new AccountDeletedException("Account already deleted");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("Soft-deleted user account: {}", user.getId());
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Long id ? id : null;
    }

    private RefreshToken validRefreshToken(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid or has been revoked"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        return token;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(jwtService.hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }
}
