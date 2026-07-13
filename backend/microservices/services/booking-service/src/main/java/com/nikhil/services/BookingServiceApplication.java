package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/*
 * Spring Boot entry point for booking-service — owns reservations, passengers, and tickets.
 *
 * Gateway: api-gateway forwards JWT-protected /api/bookings/** here (port 5000 → Eureka lb).
 * Feign: orchestrates flight-ops, pricing, seat, payment, user, airline-core, ancillary clients
 *        during create/confirm/cancel; each client resolves via Eureka service name.
 * Kafka: @EnableKafka activates payment.completed consumer and booking.confirmed producer
 *        (see KafkaConsumerConfig / KafkaProducerConfig); async ticket issuance uses @EnableAsync.
 * JPA: @EnableJpaAuditing stamps bookingDate / lastModified on Booking rows.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableFeignClients
@EnableKafka
public class BookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingServiceApplication.class, args);
	}

}
