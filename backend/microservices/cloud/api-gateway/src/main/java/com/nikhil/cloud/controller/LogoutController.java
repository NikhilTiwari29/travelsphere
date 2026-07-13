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
 * Handles JWT logout at the API Gateway.
 *
 * JWT authentication is stateless, which means that simply logging out on the
 * client does not invalidate an already-issued token. A valid JWT can normally
 * continue to access protected APIs until its expiration time.
 *
 * This controller solves that problem by revoking the token:
 *
 * Logout Flow
 * -----------
 * Client
 *   ↓
 * POST /auth/logout
 * Authorization: Bearer <JWT>
 *   ↓
 * Validate Authorization header
 *   ↓
 * Extract JWT
 *   ↓
 * Validate JWT
 *   ↓
 * Calculate remaining token lifetime
 *   ↓
 * Store token in Redis blacklist with matching TTL
 *   ↓
 * Future requests using the same JWT are rejected
 *
 * The Redis blacklist entry automatically expires when the JWT would have
 * naturally expired, preventing unnecessary permanent blacklist entries.
 *
 * Note:
 * /auth/logout is handled directly by the API Gateway because logout is a
 * token-revocation operation. The request does not need to be forwarded to
 * user-service.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class LogoutController {

    /**
     * Utility responsible for JWT validation and expiration calculations.
     */
    private final JwtUtil jwtUtil;

    /**
     * Stores revoked JWTs in Redis and allows the gateway authentication
     * filter to check whether a token has been revoked.
     */
    private final TokenBlacklistService blacklistService;

    /**
     * Revokes the JWT supplied in the Authorization header.
     *
     * Expected request:
     *
     * POST /auth/logout
     * Authorization: Bearer <JWT>
     *
     * Processing:
     * 1. Validate the Authorization header.
     * 2. Remove the "Bearer " prefix.
     * 3. Validate the JWT.
     * 4. Calculate the token's remaining lifetime.
     * 5. Store the token in Redis with that lifetime as its TTL.
     *
     * After logout, RouteConfig.jwtAuthFilter() checks the blacklist and
     * rejects any future request using the revoked token.
     *
     * @param authHeader Authorization header containing the Bearer JWT
     * @return logout result message
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(
                    value = JwtConstant.JWT_HEADER,
                    required = false
            ) String authHeader) {

        /*
         * The logout request must contain:
         *
         * Authorization: Bearer <JWT>
         *
         * If the header is missing or does not start with the expected
         * Bearer prefix, the request cannot identify which token to revoke.
         */
        if (authHeader == null ||
                !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Missing or invalid Authorization header"
                    ));
        }

        /*
         * Remove the "Bearer " prefix and keep only the raw JWT.
         *
         * Example:
         *
         * Before:
         * Bearer eyJhbGciOiJIUzUxMiJ9...
         *
         * After:
         * eyJhbGciOiJIUzUxMiJ9...
         */
        String token = authHeader.substring(
                JwtConstant.TOKEN_PREFIX.length()
        );

        /*
         * If the token is already expired or otherwise invalid,
         * there is no need to store it in Redis.
         *
         * An invalid token cannot access protected APIs anyway.
         */
        if (!jwtUtil.isTokenValid(token)) {

            return ResponseEntity.ok(
                    Map.of("message", "Token already invalid")
            );
        }

        /*
         * Calculate how much time remains before the JWT naturally expires.
         *
         * Example:
         *
         * Token expiry:       6:00 PM
         * Logout time:        4:00 PM
         * Remaining validity: 2 hours
         *
         * Redis blacklist TTL will therefore be 2 hours.
         */
        Duration ttl = jwtUtil.getRemainingValidity(token);

        /*
         * Store the JWT in Redis as revoked.
         *
         * Conceptually:
         *
         * Key:
         * jwt:blacklist:<JWT>
         *
         * Value:
         * 1
         *
         * TTL:
         * remaining JWT validity
         *
         * Redis automatically removes the blacklist entry when the
         * JWT reaches its natural expiration time.
         */
        blacklistService.blacklist(token, ttl);

        /*
         * Record successful logout without logging the actual JWT,
         * since tokens are sensitive credentials.
         */
        log.info(
                "User logged out — token blacklisted for {}s",
                ttl.toSeconds()
        );

        /*
         * At this point the JWT has been revoked.
         *
         * Any future protected request using this token will reach:
         *
         * RouteConfig.jwtAuthFilter()
         *          ↓
         * blacklistService.isBlacklisted(token)
         *          ↓
         * true
         *          ↓
         * 401 Unauthorized
         */
        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully")
        );
    }
}