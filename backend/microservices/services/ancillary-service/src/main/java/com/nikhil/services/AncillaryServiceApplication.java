package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Spring Boot entry point for ancillary-service — meals, baggage, insurance, cabin add-ons.
 *
 * Gateway: api-gateway routes /api/meals/**, /api/ancillaries/**, /api/insurance-coverages/**,
 *           /api/flight-meals/**, and /api/flight-cabin-ancillaries/** (JWT + circuit breaker).
 * Feign: @EnableFeignClients wires SeatClient, LocationClient, and AirlineClient so catalog
 *        endpoints can validate seat maps, airports, and airline ownership without local copies.
 * No Kafka — booking-service stores selected ancillary IDs; this service is read-heavy catalog data.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
public class AncillaryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AncillaryServiceApplication.class, args);
	}

}
