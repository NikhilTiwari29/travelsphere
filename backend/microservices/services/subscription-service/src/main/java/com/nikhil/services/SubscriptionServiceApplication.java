package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Spring Boot entry point for subscription-service (scaffold / future newsletter or plan feature).
 *
 * Not yet registered in api-gateway RouteConfig — no public /api/subscriptions/** route today.
 * No Feign or Kafka wiring; add @EnableFeignClients / @EnableKafka here when integrations land.
 */
@SpringBootApplication
public class SubscriptionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriptionServiceApplication.class, args);
	}

}
