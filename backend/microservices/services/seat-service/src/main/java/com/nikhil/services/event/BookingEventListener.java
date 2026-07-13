package com.nikhil.services.event;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.event.BookingConfirmedEvent;
import com.nikhil.services.service.SeatInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Kafka consumer that finalizes seat inventory after booking confirmation.
 * Downstream of booking.confirmed published by Booking Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final SeatInstanceService seatInstanceService;

    /*
     * Marks selected seats as booked after Booking Service confirms payment.
     *
     * Event Flow:
     * Booking Service → booking.confirmed
     *      ↓
     * Seat Service updates SeatInstance status to BOOKED.
     */
    @KafkaListener(topics = "booking.confirmed", groupId = "seat-service-group")
    @Transactional
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        if (event.getSeatInstanceIds() == null || event.getSeatInstanceIds().isEmpty()) {
            log.warn("No seat instance IDs in BookingConfirmedEvent for booking: {}", event.getBookingReference());
            return;
        }

        for (Long seatInstanceId : event.getSeatInstanceIds()) {
            try {
                seatInstanceService.updateSeatInstanceStatus(
                        seatInstanceId,
                        SeatAvailabilityStatus.BOOKED
                );
                log.info(
                        "Seat instance {} marked as BOOKED for booking {}",
                        seatInstanceId, event.getBookingReference()
                );
            } catch (Exception e) {
                log.error(
                        "Failed to update seat instance {} for booking {}: {}",
                        seatInstanceId, event.getBookingReference(), e.getMessage()
                );
            }
        }
    }
}
