package com.nikhil.common_lib.enums;

/**
 * Application-wide error codes shared across all microservices.
 *
 * Error codes should remain stable as they may be consumed by clients,
 * frontends, and other services.
 */
public enum ErrorCode {

    // =========================================================================
    // Common Errors
    // =========================================================================
    INTERNAL_SERVER_ERROR,
    VALIDATION_FAILED,
    BAD_REQUEST,
    RESOURCE_NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,

    // =========================================================================
    // User Service
    // =========================================================================
    USER_NOT_FOUND,
    USER_ALREADY_EXISTS,
    EMAIL_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    INVALID_PASSWORD,
    ACCOUNT_DISABLED,
    ACCOUNT_LOCKED,
    INVALID_TOKEN,
    TOKEN_EXPIRED,

    // =========================================================================
    // Location Service
    // =========================================================================
    COUNTRY_NOT_FOUND,
    CITY_NOT_FOUND,
    AIRPORT_NOT_FOUND,
    AIRPORT_ALREADY_EXISTS,

    // =========================================================================
    // Airline Service
    // =========================================================================
    AIRLINE_NOT_FOUND,
    AIRLINE_ALREADY_EXISTS,
    AIRCRAFT_NOT_FOUND,
    AIRCRAFT_ALREADY_EXISTS,
    AIRLINE_OWNERSHIP_MISMATCH,

    // =========================================================================
    // Flight Service
    // =========================================================================
    FLIGHT_NOT_FOUND,
    FLIGHT_ALREADY_EXISTS,
    FLIGHT_INSTANCE_NOT_FOUND,
    FLIGHT_SCHEDULE_NOT_FOUND,

    // =========================================================================
    // Pricing Service
    // =========================================================================
    FARE_NOT_FOUND,
    FARE_RULE_NOT_FOUND,
    BAGGAGE_POLICY_NOT_FOUND,
    BAGGAGE_POLICY_ALREADY_EXISTS,

    // =========================================================================
    // Seat Service
    // =========================================================================
    CABIN_CLASS_NOT_FOUND,
    SEAT_MAP_NOT_FOUND,
    SEAT_NOT_FOUND,
    SEAT_ALREADY_BOOKED,

    // =========================================================================
    // Ancillary Service
    // =========================================================================
    ANCILLARY_NOT_FOUND,

    // =========================================================================
    // Booking Service
    // =========================================================================
    BOOKING_NOT_FOUND,
    BOOKING_ALREADY_CANCELLED,
    BOOKING_EXPIRED,

    // =========================================================================
    // Payment Service
    // =========================================================================
    PAYMENT_FAILED,
    PAYMENT_NOT_FOUND,
    REFUND_FAILED,

    // =========================================================================
    // City Service
    // =========================================================================
    CITY_ALREADY_EXISTS,

    // =========================================================================
    // Airline Core Service
    // =========================================================================
    AIRCRAFT_OWNERSHIP_MISMATCH,
    INVALID_AIRCRAFT_CONFIGURATION,
    INVALID_SEATING_CAPACITY,
    INVALID_MANUFACTURE_YEAR,

    // =========================================================================
    // Fare Service
    // =========================================================================
    FARE_ALREADY_EXISTS,
    FARE_RULE_ALREADY_EXISTS
}