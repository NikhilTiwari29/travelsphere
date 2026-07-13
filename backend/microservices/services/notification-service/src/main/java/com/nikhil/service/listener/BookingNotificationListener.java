package com.nikhil.service.listener;

import com.nikhil.common_lib.event.BookingConfirmedEvent;
import com.nikhil.service.service.EmailService;
import com.nikhil.service.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/*
 * Kafka consumer for booking.confirmed — last mile of the booking pipeline.
 * Delegates to EmailService and SmsService; no local booking DB in this service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationListener {

    private final EmailService emailService;
    private final SmsService smsService;

    /*
     * Sends customer notifications after Booking Service confirms a booking.
     *
     * Event Flow:
     * Booking Service → booking.confirmed
     *      ↓
     * Notification Service
     *      ↓
     * EmailService and SmsService.
     */
    @KafkaListener(
            topics = "booking.confirmed",
            groupId = "notification-service-group",
            containerFactory = "bookingConfirmedKafkaListenerContainerFactory"
    )
    public void handleBookingConfirmed(@Payload BookingConfirmedEvent event) {
        log.info("Received BookingConfirmedEvent: bookingRef={}, email={}",
                event.getBookingReference(), event.getContactEmail());

        // Send email notification
        if (event.getContactEmail() != null && !event.getContactEmail().isBlank()) {
            try {
                emailService.sendBookingConfirmation(event);
                log.info("Email sent for booking={}", event.getBookingReference());
            } catch (Exception e) {
                log.error("Failed to send email for booking={}: {}", event.getBookingReference(), e.getMessage(), e);
            }
        } else {
            log.warn("No contact email for booking={}, skipping email", event.getBookingReference());
        }

        // Send SMS notification
        if (event.getContactPhone() != null && !event.getContactPhone().isBlank()) {
            try {
                smsService.sendBookingConfirmation(event);
                log.info("SMS sent for booking={}", event.getBookingReference());
            } catch (Exception e) {
                log.error("Failed to send SMS for booking={}: {}", event.getBookingReference(), e.getMessage(), e);
            }
        } else {
            log.warn("No contact phone for booking={}, skipping SMS", event.getBookingReference());
        }
    }
}
