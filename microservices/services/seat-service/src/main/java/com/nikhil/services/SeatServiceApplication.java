package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/*
 * Spring Boot entry point for the seat-service microservice.
 *
 * Gateway routes (RouteConfig.seatServiceRoutes, JWT required):
 *   /api/cabin-classes/**, /api/seat-maps/**, /api/seats/**,
 *   /api/seat-instances/**, /api/flight-instance-cabins/**
 *
 * Feign callers: booking-service (SeatClient), flight-ops-service (SeatClient).
 * Kafka consumers (group seat-service-group):
 *   flight-instance-created ← flight-ops-service
 *   booking.confirmed         ← booking-service
 *
 * Entity model: CabinClass → SeatMap → Seat → SeatInstance per flight instance.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableKafka
public class SeatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatServiceApplication.class, args);
	}

}
