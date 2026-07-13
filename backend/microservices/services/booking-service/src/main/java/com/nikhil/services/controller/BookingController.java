package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.BookingRequest;
import com.nikhil.common_lib.payload.response.BookingResponse;
import com.nikhil.common_lib.payload.response.BookingStatisticsResponse;
import com.nikhil.common_lib.payload.response.PaymentInitiateResponse;
import com.nikhil.services.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing the booking lifecycle.
 *
 * All client-facing booking operations enter through this controller and are
 * delegated to BookingService for business processing.
 *
 * Gateway flow:
 *
 * Client
 *    ↓
 * API Gateway
 *    ↓
 * BookingController
 *    ↓
 * BookingService
 *
 * The API Gateway authenticates the JWT and forwards the authenticated user
 * identifier through the X-User-Id request header.
 *
 * Booking creation flow:
 *
 * Client
 *    ↓
 * POST /api/bookings
 *    ↓
 * Booking Service
 *    ↓
 * Validate flight, fare, seat and ancillary selections
 *    ↓
 * Persist booking, passengers and ticket records
 *    ↓
 * Payment Service
 *    ↓
 * Return payment checkout information
 *
 * Payment completion is handled asynchronously:
 *
 * payment.completed
 *    ↓
 * PaymentEventListener
 *    ↓
 * Confirm Booking
 *    ↓
 * booking.confirmed
 *    ↓
 * Seat Service + Notification Service
 *
 * Logging policy:
 *   - INFO  : state-changing API operations.
 *   - DEBUG : read-only API operations and result counts.
 *   - Request DTOs are not logged to avoid exposing passenger or other
 *     potentially sensitive booking data.
 */
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;


    /**
     * Creates a new booking and initiates the payment flow.
     *
     * The service validates the requested booking components, creates the
     * initial booking state, persists related booking records, and requests
     * payment initialization from Payment Service.
     */
    @PostMapping
    public ResponseEntity<PaymentInitiateResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException, PaymentException {

        log.info(
                "Received create booking request userId={}",
                userId
        );

        PaymentInitiateResponse response =
                bookingService.createBooking(request, userId);

        log.info(
                "Booking creation flow completed and payment initialized userId={}",
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Updates an existing booking using the supplied booking details.
     *
     * Business validation and update eligibility rules are handled by
     * BookingService.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received update booking request bookingId={} userId={}",
                id,
                userId
        );

        BookingResponse response =
                bookingService.updateBooking(id, request);

        log.info(
                "Booking updated successfully bookingId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves a booking by its unique identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching booking bookingId={}",
                id
        );

        BookingResponse response =
                bookingService.getBookingById(id);

        log.debug(
                "Booking fetched successfully bookingId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves bookings belonging to the authenticated airline owner.
     *
     * Optional filters support:
     *   - Free-text search
     *   - Booking status
     *   - Flight instance
     *   - Sort direction
     *
     * The service resolves the airline associated with the authenticated
     * user and applies the requested filters.
     */
    @GetMapping("/airline")
    public ResponseEntity<List<BookingResponse>> getBookingsByAirline(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long flightInstanceId,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Fetching airline bookings userId={} status={} flightInstanceId={} sortDirection={} searchProvided={}",
                userId,
                status,
                flightInstanceId,
                sortDirection,
                search != null && !search.isBlank()
        );

        List<BookingResponse> responses =
                bookingService.getBookingsByAirline(
                        userId,
                        search,
                        status,
                        flightInstanceId,
                        sortDirection
                );

        log.debug(
                "Airline bookings fetched successfully userId={} resultCount={}",
                userId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Retrieves the booking history of the authenticated user.
     *
     * The user identity is obtained from the trusted X-User-Id header
     * populated by the API Gateway after authentication.
     */
    @GetMapping("/user/history")
    public ResponseEntity<List<BookingResponse>> getBookingsByUser(
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Fetching booking history userId={}",
                userId
        );

        List<BookingResponse> responses =
                bookingService.getBookingsByUser(userId);

        log.debug(
                "Booking history fetched successfully userId={} resultCount={}",
                userId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Cancels an existing booking.
     *
     * Current implementation delegates only the booking identifier to the
     * service. Any seat release, refund, or downstream event publication must
     * be handled separately if required by the booking cancellation workflow.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received cancel booking request bookingId={} userId={}",
                id,
                userId
        );

        BookingResponse response =
                bookingService.cancelBooking(id);

        log.info(
                "Booking cancellation completed bookingId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes a booking record by identifier.
     *
     * This endpoint should be used only when hard deletion is valid for the
     * domain. Operational booking systems commonly retain booking records for
     * audit and reconciliation purposes and use status transitions instead.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received delete booking request bookingId={} userId={}",
                id,
                userId
        );

        bookingService.deleteBooking(id);

        log.info(
                "Booking deleted successfully bookingId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.noContent().build();
    }


    /**
     * Returns the number of bookings associated with a Flight.
     *
     * This endpoint can be used for operational metrics and downstream
     * availability or reporting workflows.
     */
    @GetMapping("/count/flight/{flightId}")
    public ResponseEntity<Long> getBookingCountByFlight(
            @PathVariable Long flightId
    ) {

        log.debug(
                "Fetching booking count flightId={}",
                flightId
        );

        long count =
                bookingService.countByFlightId(flightId);

        log.debug(
                "Booking count fetched successfully flightId={} count={}",
                flightId,
                count
        );

        return ResponseEntity.ok(count);
    }


    /**
     * Returns aggregated booking statistics for an airline.
     *
     * The service is responsible for calculating booking-level metrics such
     * as counts, status distributions, revenue-related totals, or other
     * statistics represented by BookingStatisticsResponse.
     */
    @GetMapping("/statistics/airline")
    public ResponseEntity<BookingStatisticsResponse> getBookingStatisticsForAirline(
            @RequestParam Long airlineId
    ) {

        log.debug(
                "Fetching booking statistics airlineId={}",
                airlineId
        );

        BookingStatisticsResponse statistics =
                bookingService.getBookingStatisticsForAirline(airlineId);

        log.debug(
                "Booking statistics fetched successfully airlineId={}",
                airlineId
        );

        return ResponseEntity.ok(statistics);
    }
}