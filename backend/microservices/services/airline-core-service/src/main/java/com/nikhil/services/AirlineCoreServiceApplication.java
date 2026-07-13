package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Spring Boot entry point for airline-core-service — airlines and aircraft reference data.
 *
 * Gateway: /api/airlines/** and /api/aircrafts/** (JWT); GET /api/airlines is admin-only via @Order(1).
 * Caching: @EnableCaching + RedisConfig (2–6 h TTL) because airline/IATA data changes infrequently.
 * No Feign or Kafka — upstream services (booking, flight-ops, ancillary, seat) call this via Feign
 *        or through the gateway; this service owns the canonical airline/aircraft catalog.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class AirlineCoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AirlineCoreServiceApplication.class, args);
	}

}
