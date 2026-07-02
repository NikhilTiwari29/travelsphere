package com.nikhil.services.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Spring Security configuration for user-service.
 *
 * Design Choice
 * -------------
 * This service permits all HTTP requests (anyRequest().permitAll()).
 * Primary auth enforcement lives at the API Gateway, which validates
 * JWTs on /api/users/** before forwarding. Public /auth/** bypasses
 * gateway JWT checks; signup/login validate credentials in AuthServiceImpl.
 *
 * SessionCreationPolicy.STATELESS: no server-side sessions; identity
 * is carried by JWT (client) and X-User-* headers (gateway → service).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * Purpose: Configure stateless HTTP security; delegate auth to Gateway.
     * Flow: disable CSRF/CORS → STATELESS sessions → permitAll requests
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /*
     * Purpose: BCrypt encoder for signup, login, and DataInitializationComponent.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
