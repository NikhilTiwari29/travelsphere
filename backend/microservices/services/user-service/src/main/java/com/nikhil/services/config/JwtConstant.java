package com.nikhil.services.config;

/*
 * Shared JWT constants used by user-service (token creation) and
 * api-gateway (token validation via RouteConfig.jwtAuthFilter).
 *
 * SECRET_KEY must match across services. JWT_HEADER and TOKEN_PREFIX
 * define the Authorization: Bearer <token> scheme used by clients.
 */
public class JwtConstant {

    public static final String SECRET_KEY =
            "asdfghjklpoiuytrewqzxcvbnmlkjhglpouhggfdsawqwertyyuiioplmnbvcxzasdfgh";

    public static final String JWT_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private JwtConstant() {}
}
