package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Spring Boot entry point for pricing-service — fares, fare rules, and baggage policies.
 *
 * Gateway: api-gateway exposes /api/fares/**, /api/fare-rules/**, /api/baggage-policies/**
 *           to authenticated clients; flight-ops and booking-service also call via Feign internally.
 * Caching: @EnableCaching + RedisConfig (2 min TTL) shields MySQL from hot fare lookups during search.
 * No Feign or Kafka — downstream callers pull fare snapshots; this service does not orchestrate others.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class PricingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PricingServiceApplication.class, args);
	}

}
