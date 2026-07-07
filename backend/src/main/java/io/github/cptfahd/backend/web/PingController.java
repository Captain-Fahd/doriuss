package io.github.cptfahd.backend.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class PingController {

    public record PingResponse(String status, String message, Instant serverTime) {
    }

    @GetMapping("/api/v1/ping")
    public PingResponse ping() {
        return new PingResponse("ok", "pong", Instant.now());
    }
}