package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Spring Boot entry point for flight-ops-service — flights, schedules, and searchable instances.
 *
 * Gateway: api-gateway routes /api/flights/**, /api/flight-instances/**, /api/flight-schedules/**.
 * Feign: @EnableFeignClients wires PricingClient (post-search fare filter), SeatClient, LocationClient,
 *        and AirlineClient; booking-service reaches this service via FlightClient on the same paths.
 * Kafka: producer-only — KafkaProducerConfig publishes flight-instance-created for seat-service;
 *        no @EnableKafka on this class; Spring Boot auto-configures from the KafkaTemplate bean.
 * Caching: @EnableCaching uses RedisConfig for hot flight/airline lookup paths.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableCaching
public class FlightOpsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightOpsServiceApplication.class, args);
	}

}
