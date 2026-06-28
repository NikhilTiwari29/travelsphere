package com.nikhil.services.repository;

import com.nikhil.services.model.FlightSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, Long> {

    List<FlightSchedule> findByFlightId(Long flightId);
    Page<FlightSchedule> findByFlightId(Long flightId, Pageable pageable);

    List<FlightSchedule> findByFlightAirlineId(Long airlineId);

    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.flight.id = :flightId AND fs.isActive = true AND fs.startDate <= :date AND fs.endDate >= :date")
    List<FlightSchedule> findActiveSchedulesForDate(
            @Param("flightId") Long flightId,
            @Param("date") LocalDate date);

    @Query("SELECT fs FROM FlightSchedule fs WHERE fs.departureAirportId = :depId AND fs.arrivalAirportId = :arrId AND fs.isActive = true")
    List<FlightSchedule> findByRoute(
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId);

    Page<FlightSchedule> findByDepartureAirportId(Long departureAirportId, Pageable pageable);
}
