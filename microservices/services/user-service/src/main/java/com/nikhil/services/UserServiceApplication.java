package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Spring Boot entry point for the user-service microservice.
 *
 * Registers with Eureka and exposes endpoints consumed by the API Gateway:
 *   /auth/**      → RouteConfig.authRoutes() (public, no JWT)
 *   /api/users/** → RouteConfig.userServiceRoutes() (JWT required)
 */
@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
