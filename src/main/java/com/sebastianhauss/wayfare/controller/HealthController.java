package com.sebastianhauss.wayfare.controller;

import com.sebastianhauss.wayfare.dto.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // Liveness probe pinged by the Worker's scheduled() cron to keep Render's
    // free-tier container from spinning down (a cold start otherwise delays the
    // next visitor by 15s+). Intentionally does no DB/Redis work: it only proves
    // the app tier is up. It sits behind OriginAuthFilter like every other route,
    // so only the Worker (which carries the shared secret) can reach it.
    @GetMapping("/_keepalive")
    public ResponseEntity<MessageResponse> keepalive() {
        return ResponseEntity.ok(new MessageResponse("ok"));
    }
}
