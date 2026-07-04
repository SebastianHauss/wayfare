package com.sebastianhauss.wayfare.exception;

import com.sebastianhauss.wayfare.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MethodParameter methodParameter;

    @Mock
    private BindingResult bindingResult;

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ShortenCodeNotFoundException("Short code not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("Short code not found");
    }

    @Test
    void handleInvalidUrl_returns400WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidUrl(
                new InvalidUrlException("Cannot shorten a URL that points back to this service"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Cannot shorten a URL that points back to this service");
    }

    @Test
    void handleValidation_returns400WithFirstFieldError() {
        FieldError fieldError = new FieldError("shortenRequest", "url", "must match \"^https?://.+\"");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("url: must match \"^https?://.+\"");
    }

    @Test
    void handleEmailAlreadyInUse_returns409WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleEmailAlreadyInUse(
                new EmailAlreadyInUseException("Email already in use: a@b.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("Email already in use: a@b.com");
    }

    @Test
    void handleInvalidCredentials_returns401WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(
                new InvalidCredentialsException("Invalid email or password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("Invalid email or password");
    }

    @Test
    void handleInvalidRefreshToken_returns401WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidRefreshToken(
                new InvalidRefreshTokenException("Refresh token is invalid or has been revoked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("Refresh token is invalid or has been revoked");
    }

    @Test
    void handleUserNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(
                new UserNotFoundException("User no longer exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("User no longer exists");
    }

    @Test
    void handleAccountDeleted_returns403WithMessageAndCode() {
        ResponseEntity<ErrorResponse> response = handler.handleAccountDeleted(
                new AccountDeletedException("This account has been deleted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("This account has been deleted");
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_DELETED");
    }

    @Test
    void handleReactivationNotAllowed_returns409WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleReactivationNotAllowed(
                new ReactivationNotAllowedException("This account was permanently deleted and can no longer be reactivated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error())
                .isEqualTo("This account was permanently deleted and can no longer be reactivated");
    }

    @Test
    void handleEmailNotVerified_returns403WithMessageAndCode() {
        ResponseEntity<ErrorResponse> response = handler.handleEmailNotVerified(
                new EmailNotVerifiedException("Please verify your email before logging in"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("Please verify your email before logging in");
        assertThat(response.getBody().code()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void handleInvalidVerificationToken_returns400WithMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidVerificationToken(
                new InvalidVerificationTokenException("Invalid or expired verification link"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Invalid or expired verification link");
    }
}
