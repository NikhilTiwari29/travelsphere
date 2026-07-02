package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/*
 * Spring Boot entry point for location-service — cities and airports reference data.
 *
 * Gateway: /api/cities/** and /api/airports/** (JWT); POST create routes require ROLE_SYSTEM_ADMIN.
 * Caching: @EnableCaching + RedisConfig (6 h TTL) — airports/cities are static; gateway and Feign
 *          callers (flight-ops, ancillary) benefit from shared Redis-backed @Cacheable reads.
 * No Feign or Kafka — consumed by other services and the frontend through api-gateway port 5000.
 */
@SpringBootApplication
@EnableCaching
public class LocationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocationServiceApplication.class, args);
	}

}
