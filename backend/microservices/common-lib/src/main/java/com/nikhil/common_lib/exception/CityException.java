package com.nikhil.common_lib.exception;

/**
 * Thrown by flight-ops / location services for invalid city data or
 * business-rule violations involving cities.
 */
public class CityException extends Exception {
    public CityException(String message) {
        super(message);
    }
}
