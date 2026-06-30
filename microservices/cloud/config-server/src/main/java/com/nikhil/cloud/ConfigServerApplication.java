package com.nikhil.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server — centralized configuration for all microservices.
 *
 * @EnableConfigServer turns this app into a Config Server that:
 *   1. Reads config files from a Git repository (configured in application.yaml)
 *   2. Serves them via REST: GET /flight-service/default → returns flight-service config
 *   3. Supports environment-specific configs: /flight-service/dev, /flight-service/prod
 *
 * Microservices include spring-cloud-starter-config as a dependency and set:
 *   spring.config.import=configserver:http://localhost:8888
 * They'll fetch their config from this server at startup.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }

}
