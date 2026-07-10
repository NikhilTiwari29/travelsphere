package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.response.TicketResponse;
import com.nikhil.services.mapper.TicketMapper;
import com.nikhil.services.model.Ticket;
import com.nikhil.services.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for retrieving and managing tickets owned by Booking Service.
 *
 * Tickets are generated internally during the Booking creation workflow.
 * This controller does not create tickets directly.
 *
 * Ticket lifecycle:
 *
 * Booking creation
 *        ↓
 * TicketService.generateTicketsForBooking(...)
 *        ↓
 * Ticket records created locally
 *        ↓
 * Payment completed
 *        ↓
 * Booking confirmation workflow
 *        ↓
 * Ticket confirmation / issuance
 *
 * This controller exposes operations for:
 *
 * - retrieving a ticket by ticket number
 * - retrieving tickets belonging to a Booking
 * - retrieving tickets belonging to a Passenger
 * - cancelling a ticket
 * - marking a ticket as used
 * - marking a ticket as refunded
 *
 * Base route:
 *
 *     /api/tickets
 */
@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;


    /**
     * Retrieves a Ticket using its public ticket number.
     *
     * The ticket number is a business identifier and is different from
     * the internal database primary key.
     */
    @GetMapping("/{ticketNumber}")
    public ResponseEntity<TicketResponse> getTicketByNumber(
            @PathVariable String ticketNumber
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to retrieve ticket by ticket number"
        );

        Ticket ticket =
                ticketService.getTicketByNumber(ticketNumber);

        log.debug(
                "Ticket retrieved successfully ticketId={}",
                ticket.getId()
        );

        return ResponseEntity.ok(
                TicketMapper.toResponse(ticket)
        );
    }


    /**
     * Retrieves all Tickets generated for a specific Booking.
     *
     * A Booking can contain multiple Tickets because one Ticket is normally
     * generated for each Passenger included in the Booking.
     *
     * Example:
     *
     * Booking
     *    ├── Passenger A → Ticket A
     *    ├── Passenger B → Ticket B
     *    └── Passenger C → Ticket C
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<TicketResponse>> getTicketsByBooking(
            @PathVariable Long bookingId
    ) {

        log.debug(
                "Received request to retrieve tickets for bookingId={}",
                bookingId
        );

        List<Ticket> tickets =
                ticketService.getTicketsByBooking(bookingId);

        List<TicketResponse> responses =
                tickets.stream()
                        .map(TicketMapper::toResponse)
                        .toList();

        log.debug(
                "Tickets retrieved for bookingId={} ticketCount={}",
                bookingId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Retrieves all Tickets associated with a specific Passenger.
     *
     * This endpoint can return multiple records because the same Passenger
     * may have tickets associated with different Booking or travel records,
     * depending on the Passenger domain model.
     */
    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<List<TicketResponse>> getTicketsByPassenger(
            @PathVariable Long passengerId
    ) {

        log.debug(
                "Received request to retrieve tickets for passengerId={}",
                passengerId
        );

        List<Ticket> tickets =
                ticketService.getTicketsByPassenger(passengerId);

        List<TicketResponse> responses =
                tickets.stream()
                        .map(TicketMapper::toResponse)
                        .toList();

        log.debug(
                "Tickets retrieved for passengerId={} ticketCount={}",
                passengerId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Cancels an existing Ticket.
     *
     * The service layer is responsible for validating whether the current
     * Ticket status allows the cancellation transition.
     *
     * This endpoint currently performs only the Ticket state transition.
     * Refund processing and seat release should be coordinated separately
     * through the appropriate booking cancellation workflow.
     */
    @PutMapping("/{ticketId}/cancel")
    public ResponseEntity<TicketResponse> cancelTicket(
            @PathVariable Long ticketId,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received ticket cancellation request ticketId={} userId={}",
                ticketId,
                userId
        );

        Ticket ticket =
                ticketService.cancelTicket(ticketId);

        log.info(
                "Ticket cancelled successfully ticketId={} userId={}",
                ticketId,
                userId
        );

        return ResponseEntity.ok(
                TicketMapper.toResponse(ticket)
        );
    }


    /**
     * Marks a Ticket as used after the passenger has completed the
     * corresponding travel operation.
     *
     * The service layer is responsible for validating whether the Ticket
     * can transition from its current state to USED.
     */
    @PutMapping("/{ticketId}/use")
    public ResponseEntity<TicketResponse> markTicketAsUsed(
            @PathVariable Long ticketId,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to mark ticket as used ticketId={} userId={}",
                ticketId,
                userId
        );

        Ticket ticket =
                ticketService.markTicketAsUsed(ticketId);

        log.info(
                "Ticket marked as used successfully ticketId={} userId={}",
                ticketId,
                userId
        );

        return ResponseEntity.ok(
                TicketMapper.toResponse(ticket)
        );
    }


    /**
     * Marks an existing Ticket as refunded.
     *
     * This endpoint updates the Ticket lifecycle state. The actual monetary
     * refund should be performed and validated by Payment Service before the
     * Ticket is transitioned to the refunded state.
     */
    @PutMapping("/{ticketId}/refund")
    public ResponseEntity<TicketResponse> refundTicket(
            @PathVariable Long ticketId,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received ticket refund state update request ticketId={} userId={}",
                ticketId,
                userId
        );

        Ticket ticket =
                ticketService.refundTicket(ticketId);

        log.info(
                "Ticket marked as refunded successfully ticketId={} userId={}",
                ticketId,
                userId
        );

        return ResponseEntity.ok(
                TicketMapper.toResponse(ticket)
        );
    }
}