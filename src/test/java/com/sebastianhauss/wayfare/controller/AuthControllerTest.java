package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.DeleteAccountRequest;
import com.sebastianhauss.wayfare.dto.LoginRequest;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.dto.MessageResponse;
import com.sebastianhauss.wayfare.dto.RefreshRequest;
import com.sebastianhauss.wayfare.dto.RegisterRequest;
import com.sebastianhauss.wayfare.dto.ResendVerificationRequest;
import com.sebastianhauss.wayfare.dto.VerifyEmailRequest;
import com.sebastianhauss.wayfare.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    void register_returnsCreatedWithMessage() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123");
        MessageResponse response = new MessageResponse("Check your email to verify your account before logging in.");
        when(authService.register(request)).thenReturn(response);

        ResponseEntity<MessageResponse> result = authController.register(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void verifyEmail_returnsOkWithAuthResponse() {
        VerifyEmailRequest request = new VerifyEmailRequest("good-token");
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.verifyEmail(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.verifyEmail(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void resendVerification_returnsOkWithGenericMessage() {
        ResendVerificationRequest request = new ResendVerificationRequest("user@example.com");

        ResponseEntity<MessageResponse> result = authController.resendVerification(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().message()).isNotBlank();
        verify(authService).resendVerification(request);
    }

    @Test
    void login_returnsOkWithAuthResponse() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.login(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.login(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void refresh_returnsOkWithAuthResponse() {
        RefreshRequest request = new RefreshRequest("raw-refresh-token");
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.refresh(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.refresh(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void reactivate_returnsOkWithAuthResponse() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.reactivate(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.reactivate(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void logout_returnsNoContent() {
        RefreshRequest request = new RefreshRequest("raw-refresh-token");

        ResponseEntity<Void> result = authController.logout(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout(request);
    }

    @Test
    void me_returnsOkWithCurrentUser() {
        MeResponse response = new MeResponse(1L, "user@example.com", null);
        when(authService.getCurrentUser()).thenReturn(response);

        ResponseEntity<MeResponse> result = authController.me();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void deleteAccount_returnsNoContent() {
        DeleteAccountRequest request = new DeleteAccountRequest("password123");

        ResponseEntity<Void> result = authController.deleteAccount(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).deleteAccount(request);
    }
}
