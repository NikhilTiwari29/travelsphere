package com.nikhil.common_lib.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event published when payment verification fails or the gateway reports failure.
 *
 * Topic: {@code payment.failed}
 * Producer: payment-service ({@code PaymentEventProducer})
 * Consumer: booking-service ({@code PaymentEventListener}) — sets booking status
 * to CANCELLED so the pending reservation is released.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long paymentId;
    private Long bookingId;
    private Long userId;
    private Double amount;
    private String transactionId;
    private String failureReason;
    private LocalDateTime failedAt;
}
