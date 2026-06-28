package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.TicketStatus;
import com.nikhil.services.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    List<Ticket> findByBookingId(Long bookingId);

    List<Ticket> findByPassengerId(Long passengerId);

    List<Ticket> findByStatus(TicketStatus status);

    boolean existsByTicketNumber(String ticketNumber);

    @Query("SELECT t FROM Ticket t " +
            "LEFT JOIN FETCH t.booking " +
            "LEFT JOIN FETCH t.passenger " +
            "WHERE t.booking.id = :bookingId")
    List<Ticket> findByBookingIdWithDetails(@Param("bookingId") Long bookingId);
}
