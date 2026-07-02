package com.nikhil.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway — single entry point for all client traffic.
 *
 * Responsibilities:
 *   1. Route requests to downstream microservices via Eureka service discovery
 *   2. Validate JWTs and forward identity headers (X-User-Email, X-User-Id, X-User-Role)
 *   3. Apply CORS, circuit breakers, and fallback responses when services are unavailable
 *
 * Clients call only this gateway; individual service URLs are never exposed.
 */
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
