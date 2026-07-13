package com.nikhil.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/*
 * Spring Boot entry point for the notification-service microservice.
 *
 * @EnableDiscoveryClient — registers with Eureka for service discovery.
 * Consumes booking.confirmed from Kafka (BookingNotificationListener) and
 * sends email/SMS confirmations via EmailService and SmsService.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}
