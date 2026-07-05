package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthCookieService authCookieService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, authCookieService);
    }

    private AuthResult authResult() {
        AuthResponse tokens = new AuthResponse("access", "refresh", "Bearer", 900L);
        MeResponse user = new MeResponse(1L, "user@example.com", null);
        return new AuthResult(tokens, user);
    }

    @Test
    void register_returnsCreatedWithMessage() {
        MessageResponse message = new MessageResponse("Check your email");
        RegisterRequest request = new RegisterRequest("user@example.com", "password123");
        when(authService.register(request)).thenReturn(message);

        ResponseEntity<MessageResponse> result = authController.register(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(message);
    }

    @Test
    void login_setsAuthCookies_andReturnsCurrentUser() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authService.login(request)).thenReturn(authResult());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<MeResponse> result = authController.login(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().email()).isEqualTo("user@example.com");
        verify(authCookieService).setAuthCookies(response, "access", "refresh");
    }

    @Test
    void verifyEmail_setsAuthCookies_andReturnsCurrentUser() {
        VerifyEmailRequest request = new VerifyEmailRequest("token");
        when(authService.verifyEmail(request)).thenReturn(authResult());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<MeResponse> result = authController.verifyEmail(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authCookieService).setAuthCookies(response, "access", "refresh");
    }

    @Test
    void reactivate_setsAuthCookies_andReturnsCurrentUser() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authService.reactivate(request)).thenReturn(authResult());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<MeResponse> result = authController.reactivate(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authCookieService).setAuthCookies(response, "access", "refresh");
    }

    @Test
    void resendVerification_returnsOkWithMessage() {
        ResendVerificationRequest request = new ResendVerificationRequest("user@example.com");

        ResponseEntity<MessageResponse> result = authController.resendVerification(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().message()).isNotBlank();
        verify(authService).resendVerification(request);
    }

    @Test
    void refresh_setsAuthCookies_andReturnsNoContent() {
        AuthResponse tokens = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.refresh("raw-refresh-token")).thenReturn(tokens);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = authController.refresh("raw-refresh-token", response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authCookieService).setAuthCookies(response, "access", "refresh");
    }

    @Test
    void logout_clearsAuthCookies_andReturnsNoContent() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = authController.logout("raw-refresh-token", response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("raw-refresh-token");
        verify(authCookieService).clearAuthCookies(response);
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
    void deleteAccount_clearsAuthCookies_andReturnsNoContent() {
        DeleteAccountRequest request = new DeleteAccountRequest("password123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = authController.deleteAccount(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).deleteAccount(request);
        verify(authCookieService).clearAuthCookies(response);
    }
}
