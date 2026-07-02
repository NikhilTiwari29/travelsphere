package com.nikhil.common_lib.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Callback payload sent to payment-service to verify a completed checkout.
 *
 * Fields are gateway-specific: Razorpay (order id, payment id, signature) or
 * Stripe (payment intent id/status). On success, payment-service publishes
 * {@link com.nikhil.common_lib.event.PaymentCompletedEvent} to Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyRequest {

    // Razorpay specific fields
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;

    // Stripe specific fields
    private String stripePaymentIntentId;
    private String stripePaymentIntentStatus;
}
