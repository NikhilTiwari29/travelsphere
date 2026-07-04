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

/**
 * Central routing and gateway-security configuration.
 *
 * This class defines how incoming client requests are:
 *
 * 1. Matched to a route.
 * 2. Authenticated using JWT.
 * 3. Authorized based on roles where required.
 * 4. Protected using a circuit breaker.
 * 5. Load-balanced to a service instance discovered through Eureka.
 *
 *
 * HIGH-LEVEL REQUEST FLOW
 * -----------------------
 *
 * Client Request
 *      ↓
 * API Gateway
 *      ↓
 * Match RouterFunction
 *      ↓
 * JWT Authentication (protected routes)
 *      ↓
 * Role Authorization (admin routes only)
 *      ↓
 * Circuit Breaker
 *      ↓
 * Load Balancer
 *      ↓
 * Eureka Service Discovery
 *      ↓
 * Target Microservice
 *
 *
 * AUTHENTICATION VS AUTHORIZATION
 * -------------------------------
 *
 * Authentication:
 *
 *      "Who is the user?"
 *
 *      Handled by:
 *      jwtAuthFilter()
 *
 *
 * Authorization:
 *
 *      "Is this authenticated user allowed to perform this operation?"
 *
 *      Handled by:
 *      requireRole()
 *
 *
 * ROUTING TABLE
 * -------------
 *
 * /auth/**                     → user-service
 * /api/users/**                → user-service
 *
 * /api/cities/**               → location-service
 * /api/airports/**             → location-service
 *
 * /api/airlines/**             → airline-core-service
 * /api/aircrafts/**            → airline-core-service
 *
 * /api/flights/**              → flight-ops-service
 * /api/flight-instances/**     → flight-ops-service
 * /api/flight-schedules/**     → flight-ops-service
 *
 * /api/cabin-classes/**        → seat-service
 * /api/seat-maps/**            → seat-service
 * /api/seats/**                → seat-service
 *
 * /api/fares/**                → pricing-service
 * /api/fare-rules/**           → pricing-service
 * /api/baggage-policies/**     → pricing-service
 *
 * /api/bookings/**             → booking-service
 * /api/payments/**             → payment-service
 *
 *
 * IMPORTANT:
 *
 * The @Bean methods execute during application startup.
 *
 * They BUILD and REGISTER route definitions.
 *
 * They are NOT executed every time an HTTP request arrives.
 */
@Configuration
public class RouteConfig {

    /**
     * Utility responsible for:
     *
     * - JWT signature validation
     * - expiration validation
     * - email extraction
     * - authority extraction
     * - user ID extraction
     */
    private final JwtUtil jwtUtil;


    /**
     * Redis-backed JWT revocation service.
     *
     * Used to reject JWTs that are still technically valid but were
     * explicitly revoked because the user logged out.
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
     * Routes authentication-related requests to user-service.
     *
     * Examples:
     *
     * POST /auth/login
     * POST /auth/register
     *
     *
     * REQUEST FLOW
     * ------------
     *
     * Client
     *      ↓
     * /auth/**
     *      ↓
     * Circuit Breaker
     *      ↓
     * Load Balancer
     *      ↓
     * user-service
     *
     *
     * No jwtAuthFilter() is applied because users must be able to
     * log in without already having a JWT.
     *
     * Note:
     *
     * If /auth/logout is handled locally by a Gateway controller,
     * Spring MVC can resolve that local controller endpoint instead
     * of forwarding it to user-service.
     */
    @Bean
    public RouterFunction<ServerResponse> authRoutes() {

        return GatewayRouterFunctions.route("auth-routes")

                /*
                 * Match every request beginning with /auth/.
                 */
                .route(
                        RequestPredicates.path("/auth/**"),
                        HandlerFunctions.http()
                )

                /*
                 * Circuit Breaker wraps downstream communication.
                 *
                 * If user-service is unavailable, times out, or the
                 * downstream call fails according to the configured
                 * circuit-breaker policy, the request is forwarded
                 * to the local /fallback endpoint.
                 */
                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "user-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                /*
                 * Resolve an available user-service instance through
                 * Spring Cloud LoadBalancer and service discovery.
                 */
                .filter(
                        LoadBalancerFilterFunctions.lb("user-service")
                )

                .build();
    }


    // ============================================================
    // ADMIN ROUTES
    // ============================================================

    /**
     * Handles location-management operations restricted to system admins.
     *
     * Protected operations:
     *
     * POST /api/cities/**
     * POST /api/airports/**
     *
     *
     * SECURITY FLOW
     * -------------
     *
     * Request
     *      ↓
     * jwtAuthFilter()
     *      ↓
     * Validate JWT
     *      ↓
     * Extract authorities from JWT
     *      ↓
     * Add X-User-Roles to internal request
     *      ↓
     * requireRole("ROLE_SYSTEM_ADMIN")
     *      ↓
     * Allowed → location-service
     * Denied  → 403 Forbidden
     *
     *
     * @Order(1):
     *
     * These POST routes overlap with the broader location routes below.
     * The admin-specific route must have higher precedence so that privileged
     * write operations are not handled by the general authenticated route.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminLocationServiceRoutes() {

        return GatewayRouterFunctions.route("admin-location-routes")

                /*
                 * Match city creation endpoints.
                 */
                .route(
                        RequestPredicates.POST("/api/cities/**"),
                        HandlerFunctions.http()
                )

                /*
                 * Match airport creation endpoints.
                 */
                .route(
                        RequestPredicates.POST("/api/airports/**"),
                        HandlerFunctions.http()
                )

                /*
                 * Protect communication with location-service.
                 */
                .filter(
                        CircuitBreakerFilterFunctions.circuitBreaker(
                                "location-service-cb",
                                URI.create("forward:/fallback")
                        )
                )

                /*
                 * Resolve location-service through service discovery.
                 */
                .filter(
                        LoadBalancerFilterFunctions.lb("location-service")
                )

                /*
                 * Authenticate the caller.
                 *
                 * Validates JWT and creates trusted internal identity headers.
                 */
                .before(this::jwtAuthFilter)

                /*
                 * Authorize the authenticated caller.
                 *
                 * Only ROLE_SYSTEM_ADMIN is allowed.
                 */
                .before(
                        request ->
                                requireRole(
                                        request,
                                        "ROLE_SYSTEM_ADMIN"
                                )
                )

                .build();
    }


    /**
     * Handles airline administration operations.
     *
     * Current protected endpoint:
     *
     * GET /api/airlines
     *
     * Only ROLE_SYSTEM_ADMIN is allowed through this route.
     *
     * @Order(1) gives this specific admin route priority over the broader
     * /api/airlines/** route.
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> adminAirlineCoreServiceRoutes() {

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

                /*
                 * Step 1: Authentication
                 */
                .before(this::jwtAuthFilter)

                /*
                 * Step 2: Authorization
                 */
                .before(
                        request ->
                                requireRole(
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
     * Routes authenticated user-management requests to user-service.
     *
     * Any caller with a valid, non-revoked JWT can reach these routes.
     *
     * No specific role is checked here.
     */
    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {

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

                /*
                 * Require a valid JWT before forwarding.
                 */
                .before(this::jwtAuthFilter)

                .build();
    }


    /**
     * Routes authenticated airline and aircraft requests.
     *
     * @Order(2) ensures that more specific admin airline routes are evaluated
     * before this broader authenticated route.
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> airlineCoreServiceRoutes() {

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
     * Routes authenticated seat-inventory requests to seat-service.
     *
     * Handles:
     *
     * - Cabin classes
     * - Seat maps
     * - Seats
     * - Seat instances
     * - Flight-instance cabin configuration
     */
    @Bean
    public RouterFunction<ServerResponse> seatServiceRoutes() {

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
     * Routes flight-operation requests.
     *
     * Handles:
     *
     * - Flight templates
     * - Flight instances
     * - Flight schedules
     */
    @Bean
    public RouterFunction<ServerResponse> flightOpsServiceRoutes() {

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
     * Routes pricing-related requests.
     *
     * Handles:
     *
     * - Fares
     * - Fare rules
     * - Baggage policies
     */
    @Bean
    public RouterFunction<ServerResponse> pricingServiceRoutes() {

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
     * Routes ancillary-product requests.
     *
     * Handles optional travel products such as:
     *
     * - Meals
     * - Ancillary services
     * - Insurance coverage
     * - Flight meals
     * - Cabin-specific ancillaries
     */
    @Bean
    public RouterFunction<ServerResponse> AncillaryServiceRoutes() {

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
     * Handles:
     *
     * /api/cities/**
     * /api/airports/**
     *
     * This route requires authentication but does not perform a role check.
     *
     * Admin-specific POST routes are handled by
     * adminLocationServiceRoutes(), which has @Order(1).
     *
     * This general route has @Order(2).
     */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> locationServiceRoutes() {

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
     * Routes authenticated booking requests to booking-service.
     */
    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoutes() {

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
     * Routes authenticated payment requests to payment-service.
     */
    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {

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
    // GATEWAY AUTHENTICATION AND AUTHORIZATION
    // ============================================================

    /**
     * Authenticates protected requests using JWT.
     *
     *
     * COMPLETE FLOW
     * -------------
     *
     * Protected Request
     *      ↓
     * Read Authorization header
     *      ↓
     * Verify "Bearer " prefix
     *      ↓
     * Extract raw JWT
     *      ↓
     * Validate signature and expiration
     *      ↓
     * Check Redis blacklist
     *      ↓
     * Extract user identity and authorities
     *      ↓
     * Add trusted internal identity headers
     *      ↓
     * Return modified request
     *
     *
     * The downstream microservice receives:
     *
     * X-User-Id
     * X-User-Email
     * X-User-Roles
     *
     * These values are derived from the validated JWT.
     *
     * @param request incoming gateway request
     * @return authenticated request containing internal user headers
     */
    private ServerRequest jwtAuthFilter(ServerRequest request) {

        /*
         * Read:
         *
         * Authorization: Bearer eyJ...
         */
        String authHeader =
                request.headers()
                        .firstHeader(JwtConstant.JWT_HEADER);


        /*
         * Reject the request when:
         *
         * - Authorization header is missing
         * - Header does not start with the expected Bearer prefix
         */
        if (authHeader == null ||
                !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header"
            );
        }


        /*
         * Remove the "Bearer " prefix.
         *
         * Before:
         *
         * Bearer eyJhbGciOiJIUzUxMiJ9...
         *
         * After:
         *
         * eyJhbGciOiJIUzUxMiJ9...
         */
        String token = authHeader.substring(
                JwtConstant.TOKEN_PREFIX.length()
        );


        /*
         * Validate JWT signature and expiration.
         *
         * Invalid signature or expired token → 401 Unauthorized.
         */
        if (!jwtUtil.isTokenValid(token)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token"
            );
        }


        /*
         * Check whether this valid JWT has been explicitly revoked.
         *
         * Typical case:
         *
         * User logged out
         *      ↓
         * JWT stored in Redis blacklist
         *      ↓
         * Same JWT used again
         *      ↓
         * Reject with 401
         */
        if (blacklistService.isBlacklisted(token)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token has been revoked. Please log in again."
            );
        }


        /*
         * Extract trusted identity information from the validated JWT.
         */
        String email =
                jwtUtil.extractEmail(token);

        String authorities =
                jwtUtil.extractAuthorities(token);

        Long userId =
                jwtUtil.extractUserId(token);


        /*
         * Create a new ServerRequest containing internal identity headers.
         *
         * Example:
         *
         * X-User-Id: 11
         * X-User-Email: nikhiltiwari@gmail.com
         * X-User-Roles: ROLE_SYSTEM_ADMIN
         *
         * These headers can be consumed by:
         *
         * - requireRole() inside the Gateway
         * - downstream microservices
         * - audit logging
         * - ownership validation
         */
        return ServerRequest.from(request)

                .header(
                        "X-User-Id",
                        String.valueOf(userId)
                )

                .header(
                        "X-User-Email",
                        email
                )

                .header(
                        "X-User-Roles",
                        authorities
                )

                .build();
    }


    /**
     * Performs role-based authorization.
     *
     * Authentication must happen before this method.
     *
     * Example:
     *
     * JWT:
     *
     * authorities = ROLE_SYSTEM_ADMIN
     *
     * jwtAuthFilter() creates:
     *
     * X-User-Roles: ROLE_SYSTEM_ADMIN
     *
     * Then:
     *
     * requireRole(request, "ROLE_SYSTEM_ADMIN")
     *
     * Result:
     *
     * Role exists → request continues
     * Role missing → 403 Forbidden
     *
     *
     * 401 VS 403
     * ----------
     *
     * 401 Unauthorized:
     *
     * The caller is not successfully authenticated.
     *
     * Examples:
     * - missing JWT
     * - expired JWT
     * - invalid JWT
     * - revoked JWT
     *
     *
     * 403 Forbidden:
     *
     * The caller is authenticated but does not have permission
     * to perform the requested operation.
     *
     * Example:
     *
     * ROLE_CUSTOMER attempting to create a city.
     *
     *
     * @param request authenticated request
     * @param role required authority
     * @return request when authorization succeeds
     */
    private ServerRequest requireRole(
            ServerRequest request,
            String role) {

        /*
         * Read the internal role header created by jwtAuthFilter().
         */
        String roles =
                request.headers()
                        .firstHeader("X-User-Roles");


        /*
         * Reject when:
         *
         * - no role information exists
         * - required role is not present
         */
        if (roles == null || !roles.contains(role)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied. Required role: " + role
            );
        }


        /*
         * Authorization succeeded.
         *
         * Return the request unchanged so routing can continue.
         */
        return request;
    }
}