package com.nikhil.services.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis-backed Spring Cache configuration for Airline Core Service.
 *
 * Redis is used as a cache-aside optimization layer for frequently accessed
 * airline and aircraft reference data. The relational database remains the
 * authoritative source of truth.
 *
 * Cache regions use domain-specific TTLs based on expected data volatility:
 * airline data uses shorter TTLs, while relatively stable aircraft reference
 * data uses a longer TTL.
 *
 * Cache failures use a fail-open strategy so temporary Redis failures do not
 * make core airline APIs unavailable. Cache errors are logged and the
 * underlying service method continues to execute against the primary data source.
 */
@Slf4j
@Configuration
public class RedisConfig implements CachingConfigurer {

    /**
     * Configures the Redis cache manager, serialization strategy,
     * and cache-specific expiration policies.
     *
     * Cache keys are serialized as readable strings and cache values are
     * serialized as JSON with Java type metadata to support reconstruction
     * of DTOs and collection types during deserialization.
     *
     * Null values are intentionally not cached to avoid retaining temporary
     * cache misses when the underlying data may be created shortly afterward.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

        /*
         * Stores cache values as JSON with Java type metadata.
         *
         * Type metadata allows cached DTOs and collections to be restored
         * to their original Java types instead of being deserialized into
         * generic Map implementations.
         *
         * IMPORTANT:
         * Unsafe default typing should only be used when Redis is trusted
         * infrastructure and cache entries cannot be written by untrusted clients.
         */
        GenericJacksonJsonRedisSerializer jsonSerializer =
                GenericJacksonJsonRedisSerializer
                        .builder()
                        .enableUnsafeDefaultTyping()
                        .build();

        /*
         * Shared cache configuration:
         *
         * - String serializer keeps Redis keys human-readable and operationally
         *   convenient for debugging and cache inspection.
         *
         * - JSON serialization avoids Java native serialization and provides
         *   better interoperability and observability.
         *
         * - Null caching is disabled to prevent transient misses from remaining
         *   cached after data becomes available in the primary database.
         */
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration
                        .defaultCacheConfig()
                        .serializeKeysWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )
                        .serializeValuesWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(jsonSerializer)
                        )
                        .disableCachingNullValues();

        /*
         * Cache-specific TTL policy.
         *
         * Airline data:
         * 2-hour TTL balances read performance with acceptable staleness.
         * Explicit cache eviction on write operations provides immediate
         * consistency for application-managed mutations.
         *
         * Aircraft data:
         * 6-hour TTL is appropriate because aircraft model reference data
         * changes infrequently.
         */
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(

                // Airline lookup by database ID.
                "airlines",
                defaults.entryTtl(Duration.ofHours(2)),

                // Airline lookup by authenticated owner ID.
                "airlinesByOwner",
                defaults.entryTtl(Duration.ofHours(2)),

                // Airline lookup by IATA designator.
                "airlinesByIata",
                defaults.entryTtl(Duration.ofHours(2)),

                // Airline filtering and lookup by alliance.
                "airlinesByAlliance",
                defaults.entryTtl(Duration.ofHours(2)),

                // Active-airline projection used by selection dropdowns.
                "airlinesDropdown",
                defaults.entryTtl(Duration.ofHours(2)),

                // Stable aircraft model reference data.
                "aircrafts",
                defaults.entryTtl(Duration.ofHours(6))
        );

        /*
         * A 2-hour default TTL acts as a safety net for dynamically created
         * cache regions that do not have an explicit cache-specific policy.
         */
        return RedisCacheManager.builder(factory)
                .cacheDefaults(
                        defaults.entryTtl(Duration.ofHours(2))
                )
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Generates deterministic cache keys for methods whose first argument
     * is a collection.
     *
     * Collection elements are sorted before key generation so logically
     * equivalent requests produce the same cache entry regardless of input order.
     *
     * Example:
     * ["AI", "6E"] and ["6E", "AI"] both produce the same cache key.
     *
     * This prevents duplicate cache entries for semantically identical
     * batch lookup requests.
     */
    @Bean
    public KeyGenerator sortedListKeyGenerator() {

        return (Object target, Method method, Object... params) -> {

            Object first = params[0];

            if (first instanceof Collection<?> collection) {

                return collection.stream()
                        .map(Object::toString)
                        .sorted()
                        .collect(Collectors.joining(","));
            }

            /*
             * Fall back to a deterministic representation of all method
             * parameters when the first argument is not a collection.
             */
            return Arrays.deepToString(params);
        };
    }

    /**
     * Provides fail-open handling for Spring Cache operations.
     *
     * Redis is treated as an optimization layer rather than a mandatory
     * dependency for serving requests. Cache failures are logged without
     * propagating the Redis exception to API consumers.
     *
     * Behavior by operation:
     *
     * GET failure   -> service method executes and reads from the database.
     * PUT failure   -> response succeeds but the result is not cached.
     * EVICT failure -> database mutation succeeds, but stale cache data may
     *                  remain until TTL expiration.
     * CLEAR failure -> cache clear failure is logged for operational visibility.
     *
     * Note that eviction failures can temporarily expose stale data. Production
     * monitoring should alert on repeated cache eviction or clear failures.
     */
    @Override
    public CacheErrorHandler errorHandler() {

        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(
                    RuntimeException exception,
                    Cache cache,
                    Object key
            ) {
                log.warn(
                        "Cache GET failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        exception.getMessage()
                );
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception,
                    Cache cache,
                    Object key,
                    Object value
            ) {
                log.warn(
                        "Cache PUT failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        exception.getMessage()
                );
            }

            @Override
            public void handleCacheEvictError(
                    RuntimeException exception,
                    Cache cache,
                    Object key
            ) {
                log.warn(
                        "Cache EVICT failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        exception.getMessage()
                );
            }

            @Override
            public void handleCacheClearError(
                    RuntimeException exception,
                    Cache cache
            ) {
                log.warn(
                        "Cache CLEAR failed [{}]: {}",
                        cache.getName(),
                        exception.getMessage()
                );
            }
        };
    }
}