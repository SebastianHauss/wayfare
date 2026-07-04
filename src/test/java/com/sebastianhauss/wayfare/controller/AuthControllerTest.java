package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.AuthResponse;
import com.sebastianhauss.wayfare.dto.MeResponse;
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
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = authController.deleteAccount(response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).deleteAccount();
        verify(authCookieService).clearAuthCookies(response);
    }
}
