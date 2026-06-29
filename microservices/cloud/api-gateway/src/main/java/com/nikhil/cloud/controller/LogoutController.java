package com.nikhil.cloud.controller;

import com.nikhil.cloud.config.JwtConstant;
import com.nikhil.cloud.config.JwtUtil;
import com.nikhil.cloud.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Handles logout by revoking the JWT token in Redis.
 * After calling this endpoint the token is blacklisted and all subsequent
 * requests bearing it will be rejected by the gateway's JWT filter.
 *
 * <p>Route: POST /auth/logout — public (no prior auth filter applied in RouteConfig)
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class LogoutController {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = JwtConstant.JWT_HEADER, required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(JwtConstant.TOKEN_PREFIX.length());

        if (!jwtUtil.isTokenValid(token)) {
            // Already expired — nothing to do
            return ResponseEntity.ok(Map.of("message", "Token already invalid"));
        }

        Duration ttl = jwtUtil.getRemainingValidity(token);
        blacklistService.blacklist(token, ttl);

        log.info("User logged out — token blacklisted for {}s", ttl.toSeconds());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
