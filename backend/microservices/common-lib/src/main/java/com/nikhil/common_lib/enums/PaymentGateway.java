package com.nikhil.common_lib.enums;

/**
 * Supported third-party payment providers in payment-service.
 *
 * Determines which verification logic and {@link com.nikhil.common_lib.payload.request.PaymentVerifyRequest}
 * fields apply when confirming a transaction.
 */
public enum PaymentGateway {
    RAZORPAY, STRIPE
}
