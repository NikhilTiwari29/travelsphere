package com.nikhil.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Service Registry — the "phone book" of your microservice architecture.
 *
 * @EnableEurekaServer turns this app into a Eureka Server that:
 *   1. Accepts registrations from microservices (they send heartbeats every 30s)
 *   2. Maintains a registry of all running service instances and their addresses
 *   3. Provides a REST API + dashboard (http://localhost:8761) to view registered services
 *
 * Other services use @EnableDiscoveryClient (or Eureka Client starter) to register here.
 * When service A needs to call service B, it asks Eureka: "where is service B?"
 * and gets back the IP:port — no hardcoded URLs needed.
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }

}
