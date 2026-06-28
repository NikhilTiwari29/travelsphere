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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public List<Ticket> generateTicketsForBooking(Booking booking) {
        log.info("Generating tickets for booking: {}", booking.getBookingReference());

        List<Ticket> tickets = new ArrayList<>();

        for (Passenger passenger : booking.getPassengers()) {
            String ticketNumber = generateUniqueTicketNumber();

            Ticket ticket = Ticket.builder()
                    .ticketNumber(ticketNumber)
                    .status(TicketStatus.BOOKED)
                    .issuedAt(LocalDateTime.now())
                    .booking(booking)
                    .passenger(passenger)
                    .build();

            Ticket savedTicket = ticketRepository.save(ticket);
            tickets.add(savedTicket);

            log.info("Generated ticket {} for passenger {} {} on booking {}",
                    ticketNumber,
                    passenger.getFirstName(),
                    passenger.getLastName(),
                    booking.getBookingReference());
        }

        log.info("Successfully generated {} tickets for booking {}",
                tickets.size(),
                booking.getBookingReference());

        return tickets;
    }

    @Override
    @Transactional(readOnly = true)
    public Ticket getTicketByNumber(String ticketNumber) throws ResourceNotFoundException {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found with number: " + ticketNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByBooking(Long bookingId) {
        return ticketRepository.findByBookingIdWithDetails(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByPassenger(Long passengerId) {
        return ticketRepository.findByPassengerId(passengerId);
    }

    @Override
    @Transactional
    public Ticket cancelTicket(Long ticketId) throws ResourceNotFoundException {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found with id: " + ticketId));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("Cannot cancel a ticket that has already been used");
        }

        if (ticket.getStatus() == TicketStatus.REFUNDED) {
            throw new IllegalStateException("Cannot cancel a ticket that has already been refunded");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        Ticket updatedTicket = ticketRepository.save(ticket);

        log.info("Ticket {} cancelled successfully", ticket.getTicketNumber());
        return updatedTicket;
    }

    @Override
    @Transactional
    public Ticket markTicketAsUsed(Long ticketId) throws ResourceNotFoundException {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found with id: " + ticketId));

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalStateException("Cannot use a cancelled ticket");
        }

        if (ticket.getStatus() == TicketStatus.REFUNDED) {
            throw new IllegalStateException("Cannot use a refunded ticket");
        }

        ticket.setStatus(TicketStatus.USED);
        Ticket updatedTicket = ticketRepository.save(ticket);

        log.info("Ticket {} marked as used", ticket.getTicketNumber());
        return updatedTicket;
    }

    @Override
    @Transactional
    public Ticket refundTicket(Long ticketId) throws ResourceNotFoundException {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found with id: " + ticketId));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("Cannot refund a ticket that has already been used");
        }

        ticket.setStatus(TicketStatus.REFUNDED);
        Ticket updatedTicket = ticketRepository.save(ticket);

        log.info("Ticket {} refunded successfully", ticket.getTicketNumber());
        return updatedTicket;
    }

    private String generateUniqueTicketNumber() {
        String ticketNumber;
        do {
            String datePart = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
            String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            ticketNumber = String.format("TKT-%s-%s", datePart, randomPart);
        } while (ticketRepository.existsByTicketNumber(ticketNumber));

        return ticketNumber;
    }
}
