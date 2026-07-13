package com.nikhil.cloud.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Gateway-side JWT parser and validator.
 *
 * Extracts email, authorities, and userId from tokens issued by user-service,
 * and checks signature/expiry before RouteConfig forwards requests downstream.
 * Also used by LogoutController to blacklist tokens in Redis.
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil() {
        this.key = Keys.hmacShaKeyFor(JwtConstant.SECRET_KEY.getBytes());
    }

    /*
     * Parses and verifies all JWT claims using the shared signing key.
     *
     * Called by:
     * RouteConfig.jwtAuthFilter() and LogoutController
     *
     * Output:
     * Verified Claims object; invalid tokens throw and are handled by callers.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * Extracts the authenticated user's email from a verified token.
     *
     * Used by:
     * RouteConfig.jwtAuthFilter() to forward X-User-Email downstream.
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    /*
     * Extracts role/authority information from the token.
     *
     * Used by:
     * Gateway role checks and downstream service context headers.
     */
    public String extractAuthorities(String token) {
        return extractAllClaims(token).get("authorities", String.class);
    }

    /*
     * Extracts the user id used by downstream services for ownership checks.
     */
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    /*
     * Validates token signature and expiry without exposing parser exceptions.
     *
     * Output:
     * true when the gateway can trust the token; false otherwise.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Returns how long the token remains valid.
     *
     * Used by:
     * LogoutController to set the Redis blacklist TTL.
     */
    public Duration getRemainingValidity(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return Duration.between(Instant.now(), expiration.toInstant());
    }
}
