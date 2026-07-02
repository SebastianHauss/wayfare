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
}
