package com.nikhil.common_lib.exception;

/**
 * Thrown by user-service for authentication, registration, or profile errors
 * (e.g. duplicate email, invalid credentials).
 */
public class UserException extends Exception {
    public UserException(String message) {
        super(message);
    }
}
