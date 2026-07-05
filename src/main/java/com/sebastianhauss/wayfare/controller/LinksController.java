package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.LinkResponse;
import com.sebastianhauss.wayfare.dto.LinkStatsResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinksController {

    private final ShortenUrlService shortenUrlService;

    @GetMapping
    public ResponseEntity<List<LinkResponse>> getMyLinks() {
        return ResponseEntity.ok(shortenUrlService.getMyLinks());
    }

    @GetMapping("/{code}/stats")
    public ResponseEntity<LinkStatsResponse> getStats(@PathVariable String code) {
        return ResponseEntity.ok(shortenUrlService.getLinkStats(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteLink(@PathVariable String code) {
        shortenUrlService.deleteLink(code);
        return ResponseEntity.noContent().build();
    }
}
