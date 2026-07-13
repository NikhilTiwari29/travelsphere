package com.nikhil.cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration for the API Gateway.
 *
 * Allows trusted frontend origins (Vercel deployments and local dev) to call
 * the gateway with credentials and read the Authorization response header.
 */
@Configuration
public class CorsConfigs {

    /*
     * Registers a servlet-based CORS filter for Spring Cloud Gateway MVC.
     *
     * Called by:
     * Spring Boot during startup when building the servlet filter chain.
     *
     * Purpose:
     * Allows trusted frontend origins to call the Gateway and read auth headers.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(
                "https://nikhil-air.vercel.app",
                "https://nikhil-fly.vercel.app",
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setExposedHeaders(List.of("Authorization"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);  // Servlet-based filter required for Gateway MVC.
    }
}
