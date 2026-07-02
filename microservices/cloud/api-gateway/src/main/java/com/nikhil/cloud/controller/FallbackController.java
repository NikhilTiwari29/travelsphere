package com.nikhil.cloud.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Circuit-breaker fallback endpoint for the API Gateway.
 *
 * Invoked via RouteConfig forward:/fallback when a downstream service is down
 * or its circuit breaker is open, returning a stable HTTP 503 to clients.
 */
@RestController
public class FallbackController {

    /*
     * Returns a stable response when a Gateway circuit breaker opens or a downstream
     * service cannot be reached.
     *
     * Called by:
     * RouteConfig circuitBreaker(..., forward:/fallback)
     *
     * Output:
     * HTTP 503 with a client-safe error body.
     */
    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "error", "Service Unavailable",
                        "message", "The service is temporarily unavailable. Please try again later.",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
