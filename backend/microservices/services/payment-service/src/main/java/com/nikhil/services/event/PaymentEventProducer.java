package com.nikhil.services.event;

import com.nikhil.common_lib.event.PaymentCompletedEvent;
import com.nikhil.common_lib.event.PaymentFailedEvent;
import com.nikhil.services.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * Kafka bridge from Payment Service to Booking Service.
 * Topics payment.completed and payment.failed trigger booking DB updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /*
     * Publishes successful payment details for Booking Service.
     *
     * Event Flow:
     * Payment verification
     *      ↓
     * payment.completed topic
     *      ↓
     * Booking Service confirms the booking.
     */
    public void sendPaymentCompleted(Payment payment) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .providerPaymentId(payment.getProviderPaymentId())
                .paidAt(payment.getPaidAt())
                .build();

        kafkaTemplate.send("payment.completed", event);
        log.info("Published PaymentCompletedEvent for payment ID: {}, booking ID: {}",
                payment.getId(), payment.getBookingId());
    }

    /*
     * Publishes failed payment details for Booking Service.
     *
     * Event Flow:
     * Payment failure
     *      ↓
     * payment.failed topic
     *      ↓
     * Booking Service cancels the pending booking.
     */
    public void sendPaymentFailed(Payment payment) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .failedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("payment.failed", event);
        log.warn("Published PaymentFailedEvent for payment ID: {} - Reason: {}",
                payment.getId(), payment.getFailureReason());
    }
}
