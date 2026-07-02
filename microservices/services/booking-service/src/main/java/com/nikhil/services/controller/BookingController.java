package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.BookingRequest;
import com.nikhil.common_lib.payload.response.*;
import com.nikhil.services.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * REST entry point for the booking lifecycle in Booking Service.
 * Routes are reached via API Gateway with X-User-Id injected from JWT.
 *
 * Cross-service flow (create):
 *   Client → POST /api/bookings → BookingServiceImpl
 *     → Feign: flight-ops, pricing, seat, ancillary (price/validate)
 *     → Feign: payment-service /initiate → Razorpay checkout URL
 *   After payment:
 *     payment.completed (Kafka) → PaymentEventListener → booking.confirmed (Kafka)
 *     → seat-service (BOOKED) + notification-service (email/SMS)
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /*
     * Creates a PENDING booking, persists passengers/tickets locally, then
     * calls Payment Service via Feign to start checkout. Returns payment link.
     */
    @PostMapping
    public ResponseEntity<PaymentInitiateResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("X-User-Id") Long userId)
            throws ResourceNotFoundException, PaymentException {
        PaymentInitiateResponse response = bookingService.createBooking(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("X-User-Id") Long userId) throws ResourceNotFoundException {
        BookingResponse response = bookingService.updateBooking(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id) throws ResourceNotFoundException {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/airline")
    public ResponseEntity<List<BookingResponse>> getBookingsByAirline(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long flightInstanceId,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader("X-User-Id") Long userId
    ) {
        List<BookingResponse> responses = bookingService.getBookingsByAirline(
                userId,
                search,
                status,
                flightInstanceId,
                sortDirection
        );
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/history")
    public ResponseEntity<List<BookingResponse>> getBookingsByUser(
            @RequestHeader("X-User-Id") Long userId) {
        List<BookingResponse> responses = bookingService.getBookingsByUser(userId);
        return ResponseEntity.ok(responses);
    }

    /*
     * Local status update only; does not publish Kafka events or release seats.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) throws ResourceNotFoundException {
        BookingResponse response = bookingService.cancelBooking(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) throws ResourceNotFoundException {
        bookingService.deleteBooking(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count/flight/{flightId}")
    public ResponseEntity<Long> getBookingCountByFlight(@PathVariable Long flightId) {
        long count = bookingService.countByFlightId(flightId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/statistics/airline")
    public ResponseEntity<BookingStatisticsResponse> getBookingStatisticsForAirline(
            @RequestParam Long airlineId) {
        BookingStatisticsResponse statistics = bookingService.getBookingStatisticsForAirline(airlineId);
        return ResponseEntity.ok(statistics);
    }
}
