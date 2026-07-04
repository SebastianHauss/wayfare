package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.MeResponse;
import com.sebastianhauss.wayfare.dto.RefreshRequest;
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
    void refresh_returnsOkWithAuthResponse() {
        RefreshRequest request = new RefreshRequest("raw-refresh-token");
        AuthResponse response = new AuthResponse("access", "refresh", "Bearer", 900L);
        when(authService.refresh(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.refresh(request);

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
        ResponseEntity<Void> result = authController.deleteAccount();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).deleteAccount();
    }
}
