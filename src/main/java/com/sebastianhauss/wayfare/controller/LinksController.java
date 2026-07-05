package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.LinkResponse;
import com.sebastianhauss.wayfare.dto.LinkStatsResponse;
import com.sebastianhauss.wayfare.dto.PagedResponse;
import com.sebastianhauss.wayfare.service.ShortenUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinksController {

    private final ShortenUrlService shortenUrlService;

    @GetMapping
    public ResponseEntity<PagedResponse<LinkResponse>> getMyLinks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(shortenUrlService.getMyLinks(safePage, safeSize));
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
