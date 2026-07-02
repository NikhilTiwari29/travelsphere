package com.nikhil.cloud.config;

import com.nikhil.cloud.service.TokenBlacklistService;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

/*
 * Central routing configuration for Spring Cloud Gateway MVC.
 *
 * Startup Flow
 * ------------
 * Spring Boot Starts
 *     ↓
 * Scan @Configuration
 *     ↓
 * Execute all @Bean methods
 *     ↓
 * Register RouterFunctions
 *     ↓
 * Build Routing Table
 *
 * Routing Table (Conceptually)
 * ----------------------------
 * /auth/**         -> user-service
 * /api/users/**    -> user-service
 * /api/flights/**  -> flight-ops-service
 * /api/bookings/** -> booking-service
 * ...
 *
 * Runtime Flow
 * ------------
 * Client Request
 *     ↓
 * Match Request Path
 *     ↓
 * Execute Route Filters
 *     ↓
 * Resolve Service Instance (LoadBalancer + Eureka)
 *     ↓
 * Forward Request
 *
 * @Bean methods execute only once during application startup. They register
 * route definitions; they are not invoked for every incoming request.
 */
@Configuration
public class RouteConfig {

    private final JwtUtil jwtUtil;

    private final TokenBlacklistService blacklistService;

    public RouteConfig(JwtUtil jwtUtil, TokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }

    // ---------- Public Routes ----------

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match /auth/**
     *     ↓
     * LoadBalancer → user-service
     *     ↓
     * Forward Request
     *
     * No JWT validation.
     */
    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return GatewayRouterFunctions.route("auth-routes")
                .route(RequestPredicates.path("/auth/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker(
                        "user-service-cb",
                        URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .build();
    }

    // ---------- Admin Routes ----------

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * requireRole(ROLE_SYSTEM_ADMIN)
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     *
     * @Order(1) ensures admin POST routes are evaluated before general
     * location-service routes that match the same path patterns.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminLocationServiceRoutes() {
        return GatewayRouterFunctions.route("admin-location-routes")
                .route(RequestPredicates.POST("/api/cities/**"), HandlerFunctions.http())
                .route(RequestPredicates.POST("/api/airports/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("location-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("location-service"))
                .before(this::jwtAuthFilter)
                .before(request -> requireRole(request, "ROLE_SYSTEM_ADMIN"))
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * requireRole(ROLE_SYSTEM_ADMIN)
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     *
     * @Order(1) gives this admin route priority over broader airline routes.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminAirlineCoreServiceRoutes() {
        return GatewayRouterFunctions.route("admin-airline-core-routes")
                .route(RequestPredicates.GET("/api/airlines"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("airline-core-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("airline-core-service"))
                .before(this::jwtAuthFilter)
                .before(request -> requireRole(request, "ROLE_SYSTEM_ADMIN"))
                .build();
    }

    // ---------- Protected Routes ----------

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return GatewayRouterFunctions.route("user-service-routes")
                .route(RequestPredicates.path("/api/users/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("user-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     *
     * @Order(2) keeps these general routes behind the higher-priority admin
     * airline route.
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {
        return GatewayRouterFunctions.route("airline-core-routes")
                .route(RequestPredicates.path("/api/airlines/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/aircrafts/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("airline-core-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("airline-core-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> seatServiceRoutes() {
        return GatewayRouterFunctions.route("seat-service-routes")
                .route(RequestPredicates.path("/api/cabin-classes/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seat-maps/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seats/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/seat-instances/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-instance-cabins/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("seat-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("seat-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {
        return GatewayRouterFunctions.route("flight-ops-routes")
                .route(RequestPredicates.path("/api/flights/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-instances/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-schedules/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("flight-ops-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("flight-ops-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {
        return GatewayRouterFunctions.route("pricing-service-routes")
                .route(RequestPredicates.path("/api/fares/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/fare-rules/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/baggage-policies/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("pricing-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("pricing-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> AncillaryServiceRoutes() {
        return GatewayRouterFunctions.route("ancillary-service-routes")
                .route(RequestPredicates.path("/api/meals/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/ancillaries/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/insurance-coverages/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-meals/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/flight-cabin-ancillaries/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("ancillary-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("ancillary-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     *
     * @Order(2) allows admin location routes to handle privileged POST
     * operations before these general location routes are considered.
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> locationServiceRoutes() {
        return GatewayRouterFunctions.route("location-service-routes")
                .route(RequestPredicates.path("/api/cities/**"), HandlerFunctions.http())
                .route(RequestPredicates.path("/api/airports/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("location-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("location-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {
        return GatewayRouterFunctions.route("booking-service-routes")
                .route(RequestPredicates.path("/api/bookings/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("booking-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("booking-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    /*
     * Request Flow
     * ------------
     * Client
     *     ↓
     * Match Route
     *     ↓
     * jwtAuthFilter()
     *     ↓
     * LoadBalancer
     *     ↓
     * Forward Request
     */
    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {
        return GatewayRouterFunctions.route("payment-service-routes")
                .route(RequestPredicates.path("/api/payments/**"), HandlerFunctions.http())
                // CircuitBreaker is placed before LoadBalancer so it can catch failures
                // like "service not found" or "service is down" and send the request to /fallback.
                .filter(CircuitBreakerFilterFunctions.circuitBreaker("payment-service-cb", URI.create("forward:/fallback")))
                .filter(LoadBalancerFilterFunctions.lb("payment-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    // ---------- Gateway Filters ----------

    /*
     * JWT Authentication Flow
     *
     * Protected Request
     *        ↓
     * Read Authorization Header
     *        ↓
     * Validate JWT
     *        ↓
     * Check Blacklisted Token
     *        ↓
     * Extract User Details
     *        ↓
     * Add X-User-* Headers
     *        ↓
     * Return Updated Request
     *
     * Invalid token → 401 Unauthorized
     */
    private ServerRequest jwtAuthFilter(ServerRequest request) {
        String authHeader = request.headers().firstHeader(JwtConstant.JWT_HEADER);

        if (authHeader == null || !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(JwtConstant.TOKEN_PREFIX.length());

        if (!jwtUtil.isTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token");
        }

        if (blacklistService.isBlacklisted(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token has been revoked. Please log in again.");
        }

        String email = jwtUtil.extractEmail(token);
        String authorities = jwtUtil.extractAuthorities(token);
        Long userId = jwtUtil.extractUserId(token);

        return ServerRequest.from(request)
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Email", email)
                .header("X-User-Roles", authorities)
                .build();
    }

    /*
     * Role Authorization Flow
     *
     * Read X-User-Roles
     *        ↓
     * Required Role Present?
     *     ├── Yes → Return Request
     *     └── No  → 403 Forbidden
     */
    private ServerRequest requireRole(ServerRequest request, String role) {
        String roles = request.headers().firstHeader("X-User-Roles");
        if (roles == null || !roles.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied. Required role: " + role);
        }
        return request;
    }
}
