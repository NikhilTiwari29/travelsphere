package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.TicketStatus;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.services.model.Booking;
import com.nikhil.services.model.Passenger;
import com.nikhil.services.model.Ticket;
import com.nikhil.services.repository.TicketRepository;
import com.nikhil.services.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service implementation responsible for generating and managing Tickets
 * owned by Booking Service.
 *
 * One Ticket is generated for each Passenger associated with a Booking.
 * Ticket records are created during the Booking creation workflow and are
 * subsequently managed through explicit lifecycle state transitions.
 *
 * Typical lifecycle:
 *
 *     Booking creation
 *            |
 *            v
 *     Generate one Ticket per Passenger
 *            |
 *            v
 *         BOOKED
 *            |
 *       +----+---------+
 *       |              |
 *       v              v
 *     USED         CANCELLED
 *                       |
 *                       v
 *                   REFUNDED
 *
 * Ticket persistence is local to Booking Service. Ticket identifiers can
 * later be included in booking events consumed by downstream services such
 * as Notification Service.
 *
 * Read operations execute using read-only transactions by default. Methods
 * that create or modify Ticket state explicitly open read-write transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    /**
     * Prefix used for customer-facing Ticket numbers.
     */
    private static final String TICKET_NUMBER_PREFIX = "TKT";


    /**
     * Date component format used in generated Ticket numbers.
     *
     * Example:
     *
     *     20260710
     */
    private static final DateTimeFormatter TICKET_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;


    private final TicketRepository ticketRepository;


    /**
     * Generates one Ticket for every Passenger associated with the supplied
     * Booking.
     *
     * This method is called during the Booking creation workflow after the
     * Booking and Passenger relationships have been established.
     *
     * Each generated Ticket:
     *
     * - belongs to the supplied Booking
     * - belongs to exactly one Passenger
     * - receives a unique customer-facing Ticket number
     * - starts in BOOKED status
     * - records the issuance timestamp
     *
     * The entire operation runs in one transaction. If Ticket persistence
     * fails partway through the operation, all Ticket inserts performed by
     * this method are rolled back.
     *
     * @param booking Booking for which Tickets must be generated
     * @return persisted Tickets generated for the Booking
     */
    @Override
    @Transactional
    public List<Ticket> generateTicketsForBooking(Booking booking) {

        log.info(
                "Starting ticket generation bookingId={} passengerCount={}",
                booking.getId(),
                booking.getPassengers().size()
        );

        List<Ticket> generatedTickets =
                new ArrayList<>(booking.getPassengers().size());

        /*
         * Generate an independent Ticket for every Passenger in the Booking.
         *
         * The Booking and Passenger references form the local JPA relationships
         * required to identify the traveller and reservation associated with
         * each Ticket.
         */
        for (Passenger passenger : booking.getPassengers()) {

            String ticketNumber =
                    generateUniqueTicketNumber();

            Ticket ticket =
                    Ticket.builder()
                            .ticketNumber(ticketNumber)
                            .status(TicketStatus.BOOKED)
                            .issuedAt(LocalDateTime.now())
                            .booking(booking)
                            .passenger(passenger)
                            .build();

            Ticket savedTicket =
                    ticketRepository.save(ticket);

            generatedTickets.add(savedTicket);

            log.debug(
                    "Ticket generated successfully ticketId={} bookingId={} passengerId={}",
                    savedTicket.getId(),
                    booking.getId(),
                    passenger.getId()
            );
        }

        log.info(
                "Ticket generation completed bookingId={} generatedTicketCount={}",
                booking.getId(),
                generatedTickets.size()
        );

        return generatedTickets;
    }


    /**
     * Retrieves a Ticket using its customer-facing Ticket number.
     *
     * The Ticket number is a business identifier and is different from the
     * internal database primary key.
     *
     * @param ticketNumber customer-facing Ticket identifier
     * @return matching Ticket
     * @throws ResourceNotFoundException when no matching Ticket exists
     */
    @Override
    public Ticket getTicketByNumber(
            String ticketNumber
    ) throws ResourceNotFoundException {

        log.debug(
                "Retrieving ticket by business identifier"
        );

        Ticket ticket =
                ticketRepository.findByTicketNumber(ticketNumber)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Ticket lookup failed for supplied business identifier"
                            );

                            return new ResourceNotFoundException(
                                    "Ticket not found with the supplied ticket number"
                            );
                        });

        log.debug(
                "Ticket retrieved successfully ticketId={} status={}",
                ticket.getId(),
                ticket.getStatus()
        );

        return ticket;
    }


    /**
     * Retrieves all Tickets belonging to a Booking.
     *
     * The repository query may fetch associated Booking and Passenger details
     * required by the response-mapping layer.
     *
     * @param bookingId local Booking identifier
     * @return Tickets associated with the Booking
     */
    @Override
    public List<Ticket> getTicketsByBooking(Long bookingId) {

        log.debug(
                "Retrieving tickets for bookingId={}",
                bookingId
        );

        List<Ticket> tickets =
                ticketRepository.findByBookingIdWithDetails(bookingId);

        log.debug(
                "Tickets retrieved bookingId={} ticketCount={}",
                bookingId,
                tickets.size()
        );

        return tickets;
    }


    /**
     * Retrieves all Tickets associated with a Passenger.
     *
     * @param passengerId local Passenger identifier
     * @return Tickets associated with the Passenger
     */
    @Override
    public List<Ticket> getTicketsByPassenger(Long passengerId) {

        log.debug(
                "Retrieving tickets for passengerId={}",
                passengerId
        );

        List<Ticket> tickets =
                ticketRepository.findByPassengerId(passengerId);

        log.debug(
                "Tickets retrieved passengerId={} ticketCount={}",
                passengerId,
                tickets.size()
        );

        return tickets;
    }


    /**
     * Cancels an existing Ticket.
     *
     * A Ticket cannot be cancelled after it has already been used or refunded.
     * The state change is persisted through JPA dirty checking when the
     * transaction commits.
     *
     * @param ticketId local Ticket identifier
     * @return Ticket after cancellation
     * @throws ResourceNotFoundException when the Ticket does not exist
     */
    @Override
    @Transactional
    public Ticket cancelTicket(
            Long ticketId
    ) throws ResourceNotFoundException {

        log.info(
                "Starting ticket cancellation ticketId={}",
                ticketId
        );

        Ticket ticket =
                findTicketById(ticketId);

        /*
         * Validate the current state before applying the cancellation
         * transition.
         */
        if (ticket.getStatus() == TicketStatus.USED) {

            log.warn(
                    "Ticket cancellation rejected ticketId={} currentStatus={}",
                    ticketId,
                    ticket.getStatus()
            );

            throw new IllegalStateException(
                    "Cannot cancel a ticket that has already been used"
            );
        }

        if (ticket.getStatus() == TicketStatus.REFUNDED) {

            log.warn(
                    "Ticket cancellation rejected ticketId={} currentStatus={}",
                    ticketId,
                    ticket.getStatus()
            );

            throw new IllegalStateException(
                    "Cannot cancel a ticket that has already been refunded"
            );
        }

        /*
         * The Ticket entity is managed by the current persistence context.
         * Updating the state is sufficient; Hibernate dirty checking writes
         * the change when the transaction is flushed or committed.
         */
        ticket.setStatus(TicketStatus.CANCELLED);

        log.info(
                "Ticket cancelled successfully ticketId={} newStatus={}",
                ticketId,
                ticket.getStatus()
        );

        return ticket;
    }


    /**
     * Marks a Ticket as USED.
     *
     * Cancelled or refunded Tickets cannot transition to USED.
     * The service validates the current state before applying the transition.
     *
     * @param ticketId local Ticket identifier
     * @return updated Ticket
     * @throws ResourceNotFoundException when the Ticket does not exist
     */
    @Override
    @Transactional
    public Ticket markTicketAsUsed(
            Long ticketId
    ) throws ResourceNotFoundException {

        log.info(
                "Starting ticket usage transition ticketId={}",
                ticketId
        );

        Ticket ticket =
                findTicketById(ticketId);

        if (ticket.getStatus() == TicketStatus.CANCELLED) {

            log.warn(
                    "Ticket usage transition rejected ticketId={} currentStatus={}",
                    ticketId,
                    ticket.getStatus()
            );

            throw new IllegalStateException(
                    "Cannot use a cancelled ticket"
            );
        }

        if (ticket.getStatus() == TicketStatus.REFUNDED) {

            log.warn(
                    "Ticket usage transition rejected ticketId={} currentStatus={}",
                    ticketId,
                    ticket.getStatus()
            );

            throw new IllegalStateException(
                    "Cannot use a refunded ticket"
            );
        }

        /*
         * Update the managed entity. Explicit repository save is unnecessary
         * because the transaction automatically persists the state change
         * through Hibernate dirty checking.
         */
        ticket.setStatus(TicketStatus.USED);

        log.info(
                "Ticket marked as used successfully ticketId={} newStatus={}",
                ticketId,
                ticket.getStatus()
        );

        return ticket;
    }


    /**
     * Marks an eligible Ticket as REFUNDED.
     *
     * A Ticket that has already been used cannot be refunded. Monetary refund
     * execution should be coordinated with Payment Service before this state
     * transition is applied.
     *
     * @param ticketId local Ticket identifier
     * @return updated Ticket
     * @throws ResourceNotFoundException when the Ticket does not exist
     */
    @Override
    @Transactional
    public Ticket refundTicket(
            Long ticketId
    ) throws ResourceNotFoundException {

        log.info(
                "Starting ticket refund transition ticketId={}",
                ticketId
        );

        Ticket ticket =
                findTicketById(ticketId);

        if (ticket.getStatus() == TicketStatus.USED) {

            log.warn(
                    "Ticket refund transition rejected ticketId={} currentStatus={}",
                    ticketId,
                    ticket.getStatus()
            );

            throw new IllegalStateException(
                    "Cannot refund a ticket that has already been used"
            );
        }

        /*
         * Apply the lifecycle state transition. Hibernate dirty checking
         * persists the change when the transaction commits.
         */
        ticket.setStatus(TicketStatus.REFUNDED);

        log.info(
                "Ticket marked as refunded successfully ticketId={} newStatus={}",
                ticketId,
                ticket.getStatus()
        );

        return ticket;
    }


    /**
     * Loads a Ticket by its local database identifier.
     *
     * Centralizing the lookup avoids repeating identical repository and
     * exception-handling logic across Ticket lifecycle operations.
     */
    private Ticket findTicketById(
            Long ticketId
    ) throws ResourceNotFoundException {

        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> {

                    log.warn(
                            "Ticket not found ticketId={}",
                            ticketId
                    );

                    return new ResourceNotFoundException(
                            "Ticket not found with id: " + ticketId
                    );
                });
    }


    /**
     * Generates a customer-facing Ticket number.
     *
     * Format:
     *
     *     TKT-YYYYMMDD-XXXXXXXX
     *
     * Example:
     *
     *     TKT-20260710-A7F31C9D
     *
     * Components:
     *
     *     TKT       -> identifies the value as a Ticket number
     *     YYYYMMDD  -> Ticket generation date
     *     XXXXXXXX  -> random UUID-derived component
     *
     * The generated candidate is checked against the database before being
     * returned. The Ticket table should also enforce a UNIQUE constraint on
     * ticket_number because application-level existence checks alone cannot
     * guarantee uniqueness when concurrent requests generate Tickets at the
     * same time.
     *
     * @return candidate Ticket number not currently present in the database
     */
    private String generateUniqueTicketNumber() {

        String ticketNumber;

        do {

            String datePart =
                    LocalDate.now()
                            .format(TICKET_DATE_FORMAT);

            String randomPart =
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 8)
                            .toUpperCase();

            ticketNumber =
                    "%s-%s-%s".formatted(
                            TICKET_NUMBER_PREFIX,
                            datePart,
                            randomPart
                    );

        } while (
                ticketRepository.existsByTicketNumber(ticketNumber)
        );

        return ticketNumber;
    }
}