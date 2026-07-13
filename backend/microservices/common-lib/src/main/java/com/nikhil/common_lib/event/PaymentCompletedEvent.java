package com.nikhil.common_lib.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event published when payment verification succeeds.
 *
 * Topic: {@code payment.completed}
 * Producer: payment-service ({@code PaymentEventProducer})
 * Consumer: booking-service ({@code PaymentEventListener}) — sets booking status
 * to CONFIRMED and republishes {@link BookingConfirmedEvent}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long paymentId;
    private Long bookingId;
    private Long userId;
    private Double amount;
    private String transactionId;
    private String providerPaymentId;
    private LocalDateTime paidAt;
}
