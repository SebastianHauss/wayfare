package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.LinkResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinksControllerTest {

    @Mock
    private ShortenUrlService shortenUrlService;

    private LinksController linksController;

    @BeforeEach
    void setUp() {
        linksController = new LinksController(shortenUrlService);
    }

    @Test
    void getMyLinks_returnsOkWithLinks() {
        LinkResponse link = new LinkResponse("abc", "http://localhost:8080/abc", "https://example.com", null, 0L, null, null);
        when(shortenUrlService.getMyLinks()).thenReturn(List.of(link));

        ResponseEntity<List<LinkResponse>> result = linksController.getMyLinks();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsExactly(link);
    }

    @Test
    void deleteLink_returnsNoContent() {
        ResponseEntity<Void> result = linksController.deleteLink("abc");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(shortenUrlService).deleteLink("abc");
    }
}
