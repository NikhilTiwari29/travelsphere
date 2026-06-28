package com.nikhil.services.mapper;

import com.nikhil.common_lib.payload.response.TicketResponse;
import com.nikhil.services.model.Ticket;

public class TicketMapper {

    public static TicketResponse toResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .status(ticket.getStatus())
                .issuedAt(ticket.getIssuedAt())
                .bookingId(ticket.getBooking() != null ? ticket.getBooking().getId() : null)
                .bookingReference(ticket.getBooking() != null ? ticket.getBooking().getBookingReference() : null)
                .passengerId(ticket.getPassenger() != null ? ticket.getPassenger().getId() : null)
                .passengerFirstName(ticket.getPassenger() != null ? ticket.getPassenger().getFirstName() : null)
                .passengerLastName(ticket.getPassenger() != null ? ticket.getPassenger().getLastName() : null)
                .passengerEmail(ticket.getPassenger() != null ? ticket.getPassenger().getEmail() : null)
                .build();
    }
}
