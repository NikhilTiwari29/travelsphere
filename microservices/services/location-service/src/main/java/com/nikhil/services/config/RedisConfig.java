package com.nikhil.services.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;


/**
 * Configures Redis as the Spring Cache backend for city and airport data.
 *
 * Redis improves read performance, while MySQL remains the source of truth.
 * If Redis is unavailable, cache errors are logged and requests fall back
 * to the normal service/database flow.
 */
@Slf4j
@Configuration
public class RedisConfig implements CachingConfigurer {


    /**
     * Configures Redis serialization, cache regions, and TTL values.
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory) {


        /*
         * Stores cache values as JSON with Java type information.
         *
         * Type information allows cached JSON to be deserialized back into
         * CityResponse, AirportResponse, List<AirportResponse>, etc.,
         * instead of a generic LinkedHashMap.
         */
        GenericJacksonJsonRedisSerializer jsonSerializer =
                GenericJacksonJsonRedisSerializer
                        .builder()
                        .enableUnsafeDefaultTyping()
                        .build();


        /*
         * Common cache configuration:
         * - Keys are stored as strings.
         * - Values are stored as JSON.
         * - Null values are not cached.
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
         * Reference data changes rarely, so most entries use a 6-hour TTL.
         * The complete airport list uses a shorter 2-hour TTL.
         */
        Map<String, RedisCacheConfiguration> cacheConfigs =
                Map.of(
                        "airports",
                        defaults.entryTtl(Duration.ofHours(6)),

                        "airportsByIata",
                        defaults.entryTtl(Duration.ofHours(6)),

                        "airportsByCity",
                        defaults.entryTtl(Duration.ofHours(6)),

                        "allAirports",
                        defaults.entryTtl(Duration.ofHours(2)),

                        "cities",
                        defaults.entryTtl(Duration.ofHours(6)),

                        "citiesByCode",
                        defaults.entryTtl(Duration.ofHours(6))
                );


        // Builds the cache manager with a default TTL of 6 hours.
        return RedisCacheManager
                .builder(factory)

                .cacheDefaults(
                        defaults.entryTtl(Duration.ofHours(6))
                )

                .withInitialCacheConfigurations(cacheConfigs)

                .build();
    }


    /**
     * Logs Redis failures without failing the application request.
     *
     * Redis is treated as a performance layer, so cache failures allow
     * the service to continue using the database.
     */
    @Override
    public CacheErrorHandler errorHandler() {

        return new CacheErrorHandler() {


            // Cache read failed; @Cacheable method continues to the database.
            @Override
            public void handleCacheGetError(
                    RuntimeException e,
                    Cache cache,
                    Object key) {

                log.warn(
                        "Cache GET failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        e.getMessage()
                );
            }


            // Cache write failed; response still succeeds but is not cached.
            @Override
            public void handleCachePutError(
                    RuntimeException e,
                    Cache cache,
                    Object key,
                    Object value) {

                log.warn(
                        "Cache PUT failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        e.getMessage()
                );
            }


            // Cache eviction failed; stale data may remain until TTL expiry.
            @Override
            public void handleCacheEvictError(
                    RuntimeException e,
                    Cache cache,
                    Object key) {

                log.warn(
                        "Cache EVICT failed [{}] key={}: {}",
                        cache.getName(),
                        key,
                        e.getMessage()
                );
            }


            // Cache-region clear failed; log the failure and continue.
            @Override
            public void handleCacheClearError(
                    RuntimeException e,
                    Cache cache) {

                log.warn(
                        "Cache CLEAR failed [{}]: {}",
                        cache.getName(),
                        e.getMessage()
                );
            }
        };
    }
}