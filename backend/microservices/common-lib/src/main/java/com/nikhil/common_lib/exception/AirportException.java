package com.nikhil.common_lib.exception;

/**
 * Thrown by flight-ops / location services for invalid airport data or
 * business-rule violations involving airports.
 */
public class AirportException extends Exception {
    public AirportException(String message) {
        super(message);
    }
}
