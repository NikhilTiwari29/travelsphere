package com.nikhil.cloud.config;

import com.nikhil.cloud.service.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Central API Gateway routing and security configuration.
 *
 * Responsibilities:
 *
 * 1. Defines public and protected routes.
 * 2. Routes requests to downstream microservices.
 * 3. Applies circuit-breaker protection.
 * 4. Performs client-side load balancing.
 * 5. Validates JWTs for protected routes.
 * 6. Checks revoked tokens using Redis.
 * 7. Propagates trusted user identity headers.
 * 8. Performs route-level role authorization where required.
 *
 * Important:
 *
 * This configuration class does not require @Transactional because it does
 * not perform relational database persistence operations. Redis blacklist
 * lookups are independent remote operations and should not be wrapped in a
 * relational database transaction.
 */
@Slf4j
@Configuration
public class RouteConfig {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    /**
     * Utility responsible for JWT validation and claim extraction.
     */
    private final JwtUtil jwtUtil;

    /**
     * Redis-backed service used to determine whether a JWT has been revoked.
     */
    private final TokenBlacklistService blacklistService;

    public RouteConfig(
            JwtUtil jwtUtil,
            TokenBlacklistService blacklistService) {

        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }


    // ============================================================
    // PUBLIC ROUTES
    // ============================================================

    /**
     * Routes authentication requests to user-service.
     *
     * This route is intentionally public because login and registration
     * operations must be accessible without an existing JWT.
     */
    @Bean
    public RouterFunction<ServerResponse> authRoutes() {

        log.info("Registering public authentication routes: /auth/** -> user-service");

        return GatewayRouterFunctions.route("auth-routes")

                .route(
                        RequestPredicates.path("/auth/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "user-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("user-service")
                )

                .build();
    }


    // ============================================================
    // ADMIN ROUTES
    // ============================================================

    /**
     * Routes privileged city and airport write operations.
     *
     * Only authenticated users having ROLE_SYSTEM_ADMIN are allowed.
     *
     * @Order(1) ensures these specific POST routes are evaluated before
     * the broader authenticated location routes.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminLocationServiceRoutes() {

        log.info(
                "Registering admin location routes: POST /api/cities/**, " +
                        "POST /api/airports/** -> location-service"
        );

        return GatewayRouterFunctions.route("admin-location-routes")

                .route(
                        RequestPredicates.POST("/api/cities/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.POST("/api/airports/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "location-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("location-service")
                )

                /*
                 * Authentication must run before authorization because
                 * requireRole() reads the trusted role header created by
                 * jwtAuthFilter().
                 */
                .before(this::jwtAuthFilter)

                .before(
                        request -> requireRole(
                                request,
                                "ROLE_SYSTEM_ADMIN"
                        )
                )

                .build();
    }


    /**
     * Routes privileged airline administration operations.
     *
     * GET /api/airlines requires ROLE_SYSTEM_ADMIN.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminAirlineCoreServiceRoutes() {

        log.info(
                "Registering admin airline route: GET /api/airlines " +
                        "-> airline-core-service"
        );

        return GatewayRouterFunctions.route(
                        "admin-airline-core-routes"
                )

                .route(
                        RequestPredicates.GET("/api/airlines"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "airline-core-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb(
                                "airline-core-service"
                        )
                )

                .before(this::jwtAuthFilter)

                .before(
                        request -> requireRole(
                                request,
                                "ROLE_SYSTEM_ADMIN"
                        )
                )

                .build();
    }


    // ============================================================
    // GENERAL PROTECTED ROUTES
    // ============================================================

    /**
     * Routes authenticated user-management requests.
     */
    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {

        log.info("Registering protected user routes: /api/users/** -> user-service");

        return GatewayRouterFunctions.route("user-service-routes")

                .route(
                        RequestPredicates.path("/api/users/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "user-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("user-service")
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated airline and aircraft requests.
     *
     * @Order(2) ensures the more specific admin airline route has
     * precedence over this general route.
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {

        log.info(
                "Registering airline-core routes: /api/airlines/**, " +
                        "/api/aircrafts/** -> airline-core-service"
        );

        return GatewayRouterFunctions.route("airline-core-routes")

                .route(
                        RequestPredicates.path("/api/airlines/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/aircrafts/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "airline-core-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb(
                                "airline-core-service"
                        )
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated seat inventory requests.
     */
    @Bean
    public RouterFunction<ServerResponse> seatServiceRoutes() {

        log.info("Registering protected seat-service routes");

        return GatewayRouterFunctions.route("seat-service-routes")

                .route(
                        RequestPredicates.path("/api/cabin-classes/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/seat-maps/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/seats/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/seat-instances/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path(
                                "/api/flight-instance-cabins/**"
                        ),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "seat-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("seat-service")
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated flight operation requests.
     */
    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {

        log.info("Registering protected flight-ops-service routes");

        return GatewayRouterFunctions.route("flight-ops-routes")

                .route(
                        RequestPredicates.path("/api/flights/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/flight-instances/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/flight-schedules/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "flight-ops-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb(
                                "flight-ops-service"
                        )
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated pricing requests.
     */
    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {

        log.info("Registering protected pricing-service routes");

        return GatewayRouterFunctions.route("pricing-service-routes")

                .route(
                        RequestPredicates.path("/api/fares/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/fare-rules/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/baggage-policies/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "pricing-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("pricing-service")
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated ancillary-product requests.
     */
    @Bean
    public RouterFunction<ServerResponse> ancillaryServiceRoutes() {

        log.info("Registering protected ancillary-service routes");

        return GatewayRouterFunctions.route("ancillary-service-routes")

                .route(
                        RequestPredicates.path("/api/meals/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/ancillaries/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path(
                                "/api/insurance-coverages/**"
                        ),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/flight-meals/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path(
                                "/api/flight-cabin-ancillaries/**"
                        ),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "ancillary-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb(
                                "ancillary-service"
                        )
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes general authenticated location requests.
     *
     * Specific admin POST routes are handled by the higher-priority
     * adminLocationServiceRoutes() function.
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> locationServiceRoutes() {

        log.info("Registering general protected location-service routes");

        return GatewayRouterFunctions.route(
                        "location-service-routes"
                )

                .route(
                        RequestPredicates.path("/api/cities/**"),
                        HandlerFunctions.http()
                )

                .route(
                        RequestPredicates.path("/api/airports/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "location-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb(
                                "location-service"
                        )
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated booking requests.
     */
    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {

        log.info(
                "Registering protected booking routes: " +
                        "/api/bookings/** -> booking-service"
        );

        return GatewayRouterFunctions.route("booking-service-routes")

                .route(
                        RequestPredicates.path("/api/bookings/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "booking-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("booking-service")
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated payment requests.
     */
    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {

        log.info(
                "Registering protected payment routes: " +
                        "/api/payments/** -> payment-service"
        );

        return GatewayRouterFunctions.route("payment-service-routes")

                .route(
                        RequestPredicates.path("/api/payments/**"),
                        HandlerFunctions.http()
                )

                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "payment-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                .filter(
                        LoadBalancerFilterFunctions.lb("payment-service")
                )

                .before(this::jwtAuthFilter)

                .build();
    }


    // ============================================================
    // AUTHENTICATION
    // ============================================================

    /**
     * Authenticates a protected Gateway request.
     *
     * Processing flow:
     *
     * 1. Read Authorization header.
     * 2. Validate Bearer token format.
     * 3. Extract raw JWT.
     * 4. Validate JWT signature and expiration.
     * 5. Check Redis token blacklist.
     * 6. Extract trusted identity claims.
     * 7. Add internal identity headers.
     *
     * Security note:
     *
     * The raw JWT is deliberately not written to application logs.
     *
     * @param request incoming protected request
     * @return request enriched with trusted identity headers
     */
    private ServerRequest jwtAuthFilter(ServerRequest request) {

        String method = request.method().name();
        String path = request.uri().getPath();

        log.debug(
                "Authenticating gateway request: method={}, path={}",
                method,
                path
        );

        /*
         * Read the Authorization header.
         *
         * Expected format:
         *
         * Authorization: Bearer <JWT>
         */
        String authHeader = request.headers()
                .firstHeader(JwtConstant.JWT_HEADER);


        /*
         * Reject requests without a correctly formatted Bearer token.
         */
        if (authHeader == null ||
                !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {

            log.warn(
                    "Authentication rejected: missing or invalid Authorization " +
                            "header, method={}, path={}",
                    method,
                    path
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header"
            );
        }


        /*
         * Remove the Bearer prefix and retain only the raw JWT.
         *
         * Never log this value.
         */
        String token = authHeader.substring(
                JwtConstant.TOKEN_PREFIX.length()
        );


        /*
         * Validate token signature, structure and expiration.
         */
        if (!jwtUtil.isTokenValid(token)) {

            log.warn(
                    "Authentication rejected: invalid or expired JWT, " +
                            "method={}, path={}",
                    method,
                    path
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token"
            );
        }


        /*
         * Reject a JWT that has been explicitly revoked, for example
         * after logout.
         */
        if (blacklistService.isBlacklisted(token)) {

            log.warn(
                    "Authentication rejected: revoked JWT, " +
                            "method={}, path={}",
                    method,
                    path
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token has been revoked. Please log in again."
            );
        }


        /*
         * Extract identity information only after successful validation.
         */
        String email = jwtUtil.extractEmail(token);
        String authorities = jwtUtil.extractAuthorities(token);
        Long userId = jwtUtil.extractUserId(token);


        log.info(
                "Gateway authentication successful: userId={}, roles={}, " +
                        "method={}, path={}",
                userId,
                authorities,
                method,
                path
        );


        /*
         * Create a new immutable ServerRequest containing trusted internal
         * identity headers for downstream authorization and ownership checks.
         */
        return ServerRequest.from(request)

                .header(
                        USER_ID_HEADER,
                        String.valueOf(userId)
                )

                .header(
                        USER_EMAIL_HEADER,
                        email
                )

                .header(
                        USER_ROLES_HEADER,
                        authorities
                )

                .build();
    }


    // ============================================================
    // AUTHORIZATION
    // ============================================================

    /**
     * Performs Gateway-level role authorization.
     *
     * This method must execute after jwtAuthFilter() because it relies on
     * the trusted X-User-Roles header generated from the validated JWT.
     *
     * @param request authenticated request
     * @param requiredRole authority required by the route
     * @return original request when authorization succeeds
     */
    private ServerRequest requireRole(
            ServerRequest request,
            String requiredRole) {

        String path = request.uri().getPath();

        /*
         * Read the trusted role information added during authentication.
         */
        String roles = request.headers()
                .firstHeader(USER_ROLES_HEADER);


        log.debug(
                "Checking gateway authorization: path={}, requiredRole={}, roles={}",
                path,
                requiredRole,
                roles
        );


        /*
         * Reject an authenticated caller that does not have the required
         * authority.
         */
        if (roles == null || !hasRole(roles, requiredRole)) {

            log.warn(
                    "Gateway authorization denied: path={}, requiredRole={}, roles={}",
                    path,
                    requiredRole,
                    roles
            );

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied. Required role: " + requiredRole
            );
        }


        log.info(
                "Gateway authorization successful: path={}, requiredRole={}",
                path,
                requiredRole
        );

        return request;
    }


    /**
     * Performs an exact role match instead of using String.contains().
     *
     * This avoids accidental partial matches when multiple authorities
     * are stored in a comma-separated string.
     *
     * Example:
     *
     * roles:
     * ROLE_CUSTOMER,ROLE_SYSTEM_ADMIN
     *
     * requiredRole:
     * ROLE_SYSTEM_ADMIN
     *
     * Result:
     * true
     */
    private boolean hasRole(
            String roles,
            String requiredRole) {

        if (roles == null || roles.isBlank()) {
            return false;
        }

        for (String role : roles.split(",")) {

            if (requiredRole.equals(role.trim())) {
                return true;
            }
        }

        return false;
    }
}