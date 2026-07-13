package com.nikhil.common_lib.exception;

/**
 * Generic not-found signal used across services when an entity id does not exist
 * (booking, flight, user, fare, etc.).
 */
public class ResourceNotFoundException extends Exception {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
