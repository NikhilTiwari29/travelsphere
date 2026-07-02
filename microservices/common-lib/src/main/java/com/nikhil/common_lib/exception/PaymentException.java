package com.nikhil.common_lib.exception;

/**
 * Thrown by payment-service when initiation, verification, or gateway
 * interaction fails (invalid signature, declined payment, etc.).
 */
public class PaymentException extends Exception {

    public PaymentException(String message) {
        super(message);
    }
}
