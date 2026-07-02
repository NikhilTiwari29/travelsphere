package com.nikhil.common_lib.exception;

/**
 * Thrown when an authenticated user attempts an action their role or ownership
 * does not allow (e.g. accessing another user's booking).
 */
public class OperationNotPermittedException extends Exception {
    public OperationNotPermittedException(String message) {
        super(message);
    }
}
