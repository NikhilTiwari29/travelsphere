package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Spring Boot entry point for the payment-service microservice.
 *
 * @EnableJpaAuditing — auto-populates Payment.createdAt / updatedAt
 * @EnableFeignClients — wires UserClient for payer details during initiate
 *
 * Consumed by Booking Service (Feign) and the frontend via the API Gateway.
 * Emits payment.completed / payment.failed Kafka events after Razorpay verify.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
