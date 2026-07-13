package com.nikhil.cloud.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages revoked JWT tokens using Redis.
 *
 * JWT authentication is stateless. Once a JWT is issued, it normally remains
 * valid until its expiration time, even if the user logs out.
 *
 * This service provides token revocation by storing logged-out JWTs in Redis.
 *
 * Blacklist Flow
 * --------------
 * User logs out
 *      ↓
 * LogoutController extracts JWT
 *      ↓
 * Calculate remaining JWT validity
 *      ↓
 * blacklist(token, ttl)
 *      ↓
 * Store token in Redis
 *
 * Example Redis entry:
 *
 * Key:
 * jwt:blacklist:eyJhbGciOiJIUzUxMiJ9...
 *
 * Value:
 * 1
 *
 * TTL:
 * Remaining lifetime of the JWT
 *
 * Request Validation Flow
 * -----------------------
 * Protected API request
 *      ↓
 * Gateway validates JWT
 *      ↓
 * isBlacklisted(token)
 *      ↓
 * Check Redis
 *      ↓
 * Token exists?
 *    ├── Yes → Token was revoked → Reject request
 *    └── No  → Token is not revoked → Continue request
 *
 * The Redis entry automatically expires when the JWT reaches its natural
 * expiration time. This avoids keeping unnecessary blacklist records forever.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    /**
     * Prefix used for all JWT blacklist keys stored in Redis.
     *
     * Example:
     *
     * Raw JWT:
     * eyJhbGciOiJIUzUxMiJ9...
     *
     * Redis key:
     * jwt:blacklist:eyJhbGciOiJIUzUxMiJ9...
     *
     * Using a prefix keeps Redis keys organized and prevents collisions
     * with keys belonging to other application features.
     */
    private static final String PREFIX = "jwt:blacklist:";

    /**
     * Spring Redis helper for working with String keys and String values.
     *
     * Used here to:
     *
     * 1. Store revoked JWT tokens.
     * 2. Assign TTL values to blacklist entries.
     * 3. Check whether a JWT exists in the blacklist.
     *
     * Conceptually, this service performs operations similar to:
     *
     * SET jwt:blacklist:<token> 1 EX <seconds>
     *
     * and:
     *
     * EXISTS jwt:blacklist:<token>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Adds a JWT to the Redis blacklist.
     *
     * The blacklist entry is stored only for the remaining lifetime of
     * the token.
     *
     * Example:
     *
     * JWT issued:           10:00 AM
     * JWT expires:           6:00 PM
     * User logs out:         4:00 PM
     * Remaining validity:    2 hours
     *
     * Redis entry TTL:       2 hours
     *
     * At 6:00 PM:
     *
     * JWT naturally expires
     *          +
     * Redis automatically removes blacklist entry
     *
     * @param token raw JWT without the "Bearer " prefix
     * @param ttl   remaining duration before the JWT naturally expires
     */
    public void blacklist(String token, Duration ttl) {

        /*
         * Do not store the token if it has already expired.
         *
         * A zero or negative TTL means the JWT has no remaining validity,
         * so blacklisting it would provide no additional protection.
         */
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        try {

            /*
             * Store the JWT in Redis.
             *
             * PREFIX + token creates a key such as:
             *
             * jwt:blacklist:eyJhbGciOiJIUzUxMiJ9...
             *
             * "1" is used as a simple marker value because only the
             * existence of the key matters.
             *
             * ttl tells Redis when to automatically delete the key.
             *
             * Conceptually:
             *
             * SET jwt:blacklist:<token> 1 EX <remaining-seconds>
             */
            redisTemplate.opsForValue()
                    .set(PREFIX + token, "1", ttl);

            /*
             * Log the blacklist duration for debugging.
             *
             * The actual JWT is intentionally not logged because JWTs
             * are credentials and should not appear in application logs.
             */
            log.debug(
                    "Token blacklisted for {}s",
                    ttl.toSeconds()
            );

        } catch (Exception e) {

            /*
             * Redis failure policy: FAIL OPEN.
             *
             * If Redis is unavailable, logout continues instead of
             * failing the entire request.
             *
             * However, because the token was not stored in Redis,
             * it may remain usable until its natural expiration.
             *
             * This is an availability-over-security tradeoff.
             */
            log.warn(
                    "Redis unavailable — token blacklisting skipped: {}",
                    e.getMessage()
            );
        }
    }

    /**
     * Checks whether a JWT has been revoked.
     *
     * Called by the gateway authentication filter for protected requests.
     *
     * Request Flow
     * ------------
     * Request with JWT
     *      ↓
     * Validate signature and expiry
     *      ↓
     * isBlacklisted(token)
     *      ↓
     * Redis key exists?
     *
     * true:
     * Token was revoked, usually because the user logged out.
     * The gateway should reject the request.
     *
     * false:
     * Token is not present in the blacklist.
     * The gateway can continue processing the request.
     *
     * @param token raw JWT without the "Bearer " prefix
     * @return true if the token has been revoked, otherwise false
     */
    public boolean isBlacklisted(String token) {

        try {

            /*
             * Check whether Redis contains:
             *
             * jwt:blacklist:<token>
             *
             * hasKey() returns:
             *
             * true  → token exists in blacklist
             * false → token is not blacklisted
             */
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(PREFIX + token)
            );

        } catch (Exception e) {

            /*
             * Redis failure policy: FAIL OPEN.
             *
             * If Redis cannot be reached, the gateway treats the token
             * as not blacklisted and allows normal JWT validation to continue.
             *
             * Flow:
             *
             * Redis unavailable
             *        ↓
             * Cannot verify blacklist
             *        ↓
             * Return false
             *        ↓
             * Token treated as not revoked
             *
             * Advantage:
             * Redis downtime does not block every authenticated API request.
             *
             * Risk:
             * A previously revoked JWT may temporarily work while Redis
             * is unavailable.
             */
            log.warn(
                    "Redis unavailable for blacklist check — " +
                            "treating token as valid: {}",
                    e.getMessage()
            );

            return false;
        }
    }
}