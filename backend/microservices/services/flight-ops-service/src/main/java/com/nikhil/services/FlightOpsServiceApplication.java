package com.nikhil.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableCaching
public class FlightOpsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightOpsServiceApplication.class, args);
	}

}
