package com.nikhil.cloud.config;

/**
 * Shared JWT header names and signing key used by the API Gateway.
 *
 * Must stay in sync with user-service JwtConstant so tokens issued at login
 * can be verified here. SECRET_KEY signs and validates all gateway JWT checks.
 */
public class JwtConstant {

    public static final String SECRET_KEY =
            "asdfghjklpoiuytrewqzxcvbnmlkjhglpouhggfdsawqwertyyuiioplmnbvcxzasdfgh";

    public static final String JWT_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private JwtConstant() {}
}
