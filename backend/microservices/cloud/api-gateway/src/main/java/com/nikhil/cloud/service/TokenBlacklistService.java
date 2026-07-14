package com.nikhil.cloud.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages JWT token revocation using Redis.
 *
 * Since JWTs are stateless, issued tokens remain valid until they expire.
 * This service implements logout by storing revoked tokens in Redis for the
 * remainder of their lifetime. Incoming requests consult the blacklist to
 * reject revoked tokens, while Redis automatically removes expired entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    /**
     * Prefix applied to all JWT blacklist entries stored in Redis.
     *
     * Example:
     * jwt:blacklist:<jwt>
     */
    private static final String PREFIX = "jwt:blacklist:";

    /**
     * Redis client used to store and query blacklisted JWTs.
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Stores a revoked JWT in Redis for its remaining lifetime.
     *
     * @param token raw JWT without the "Bearer " prefix
     * @param ttl remaining validity duration of the JWT
     */
    public void blacklist(String token, Duration ttl) {

        // Skip already expired tokens.
        if (ttl.isNegative() || ttl.isZero()) {
            log.debug("Skipping JWT blacklist because the token has already expired.");
            return;
        }

        try {

            redisTemplate.opsForValue()
                    .set(PREFIX + token, "1", ttl);

            log.info(
                    "JWT token successfully blacklisted (ttl={} seconds).",
                    ttl.toSeconds()
            );

        } catch (Exception e) {

            /*
             * Fail-open strategy:
             * Logout succeeds even if Redis is unavailable. The JWT may remain
             * usable until its natural expiration because it could not be
             * persisted in the blacklist.
             */
            log.warn(
                    "Failed to store JWT blacklist entry in Redis. Logout will continue with fail-open behavior.",
                    e
            );
        }
    }

    /**
     * Checks whether a JWT has been revoked.
     *
     * @param token raw JWT without the "Bearer " prefix
     * @return true if the JWT is blacklisted; otherwise false
     */
    public boolean isBlacklisted(String token) {

        try {

            boolean blacklisted = Boolean.TRUE.equals(
                    redisTemplate.hasKey(PREFIX + token)
            );

            if (blacklisted) {
                log.debug("Rejected request because the JWT is blacklisted.");
            }

            return blacklisted;

        } catch (Exception e) {

            /*
             * Fail-open strategy:
             * If Redis cannot be reached, blacklist verification is skipped
             * and normal JWT validation continues.
             */
            log.warn(
                    "Unable to verify JWT blacklist status because Redis is unavailable. Proceeding with fail-open behavior.",
                    e
            );

            return false;
        }
    }
}