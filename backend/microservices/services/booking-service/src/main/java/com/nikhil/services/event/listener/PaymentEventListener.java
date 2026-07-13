package com.nikhil.services.event.listener;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.event.PaymentCompletedEvent;
import com.nikhil.common_lib.event.PaymentFailedEvent;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.clients.FlightClient;
import com.nikhil.services.clients.PricingClient;
import com.nikhil.services.clients.UserClient;
import com.nikhil.services.event.publisher.BookingEventProducer;
import com.nikhil.services.model.Booking;
import com.nikhil.services.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Bridges Payment Service Kafka events into booking state and downstream fan-out.
 * On success: DB update → Feign enrichment → booking.confirmed for seat + notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final BookingRepository bookingRepository;
    private final BookingEventProducer bookingEventProducer;
    private final FlightClient flightClient;
    private final PricingClient pricingClient;
    private final UserClient userClient;

    /*
     * Confirms a booking after Payment Service reports successful payment.
     *
     * Event Flow:
     * Payment Service → payment.completed → Booking Service
     *      ↓
     * Update booking status
     *      ↓
     * Fetch enrichment data through Feign clients
     *      ↓
     * Publish booking.confirmed
     */
    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent for bookingId={}", event.getBookingId());

        Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);
        if (booking == null) {
            log.error("Booking not found for id={}", event.getBookingId());
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(event.getPaymentId());
        booking = bookingRepository.save(booking);
        log.info("Booking {} confirmed after payment {}", booking.getBookingReference(), event.getPaymentId());

        // Fetch enrichment data for notification — failures are non-fatal
        FlightInstanceResponse flightInstance = fetchFlightInstance(booking.getFlightInstanceId());
        FareResponse fareResponse = fetchFare(booking.getFareId());
        UserDTO userDTO = fetchUser(booking.getUserId());

        // Publish enriched event (seat-service + notification-service both consume it)
        bookingEventProducer.sendBookingConfirmed(booking, event, flightInstance, fareResponse, userDTO);
    }

    /*
     * Cancels a booking when Payment Service reports payment failure.
     *
     * Side effect:
     * Updates booking status in the Booking database.
     */
    @KafkaListener(topics = "payment.failed", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for bookingId={}", event.getBookingId());

        Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);
        if (booking == null) {
            log.error("Booking not found for id={}", event.getBookingId());
            return;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.warn("Booking {} cancelled due to payment failure: {}",
                booking.getBookingReference(), event.getFailureReason());
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /*
     * Calls Flight Ops Service for notification data.
     * Failure is non-fatal because booking confirmation must not be rolled back
     * only because enrichment data is unavailable.
     */
    private FlightInstanceResponse fetchFlightInstance(Long flightInstanceId) {
        if (flightInstanceId == null) return null;
        try {
            return flightClient.getFlightInstanceResponse(flightInstanceId);
        } catch (Exception e) {
            log.warn("Could not fetch FlightInstance id={} for notification enrichment: {}",
                    flightInstanceId, e.getMessage());
            return null;
        }
    }

    /*
     * Calls Pricing Service for fare and baggage details used in notifications.
     */
    private FareResponse fetchFare(Long fareId) {
        if (fareId == null) return null;
        try {
            return pricingClient.getFareById(fareId);
        } catch (Exception e) {
            log.warn("Could not fetch Fare id={} for notification enrichment: {}",
                    fareId, e.getMessage());
            return null;
        }
    }

    /*
     * Calls User Service to personalize the booking confirmation message.
     */
    private UserDTO fetchUser(Long userId) {
        if (userId == null) return null;
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Could not fetch User id={} for notification enrichment: {}",
                    userId, e.getMessage());
            return null;
        }
    }
}
