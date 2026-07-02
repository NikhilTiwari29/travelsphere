package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.services.model.FlightInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA access for FlightInstance rows owned by flight-ops-service.
 * Used by search (Specification), airline dashboards, and booking batch lookups.
 * Custom queries filter by airline/airports/date; pessimistic lock supports seat holds.
 */
public interface FlightInstanceRepository extends JpaRepository<
        FlightInstance, Long>,
        JpaSpecificationExecutor<FlightInstance> {



    /**
     * Paginated airline dashboard query with optional airport/flight/date filters.
     */
    @Query("SELECT fi FROM FlightInstance fi WHERE fi.airlineId = :airlineId" +
            " AND (:departureAirportId IS NULL OR fi.departureAirportId = :departureAirportId)" +
            " AND (:arrivalAirportId IS NULL OR fi.arrivalAirportId = :arrivalAirportId)" +
            " AND (:flightId IS NULL OR fi.flight.id = :flightId)" +
            " AND (:dayStart IS NULL OR fi.departureDateTime >= :dayStart)" +
            " AND (:dayEnd IS NULL OR fi.departureDateTime < :dayEnd)")
    Page<FlightInstance> findByAirlineIdWithFilters(
            @Param("airlineId") Long airlineId,
            @Param("departureAirportId") Long departureAirportId,
            @Param("arrivalAirportId") Long arrivalAirportId,
            @Param("flightId") Long flightId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            Pageable pageable);

    Page<FlightInstance> findByStatus(FlightStatus status, Pageable pageable);

    /**
     * Legacy non-paged search by route and departure window (SCHEDULED only).
     */
    @Query("SELECT fi FROM FlightInstance fi WHERE fi.departureAirportId = :depId AND fi.arrivalAirportId = :arrId AND fi.departureDateTime >= :fromDate AND fi.departureDateTime <= :toDate AND fi.status = 'SCHEDULED'")
    List<FlightInstance> searchFlights(
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    /**
     * Paginated variant of route/date search for older search endpoints.
     */
    @Query("SELECT fi FROM FlightInstance fi WHERE fi.departureAirportId = :depId AND fi.arrivalAirportId = :arrId AND fi.departureDateTime >= :fromDate AND fi.departureDateTime <= :toDate")
    Page<FlightInstance> searchFlightsPaged(
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /**
     * Pessimistic write lock for concurrent seat-inventory updates during booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fi FROM FlightInstance fi WHERE fi.id = :id")
    Optional<FlightInstance> findByIdForUpdate(@Param("id") Long id);

    Long countByFlightIdAndStatus(Long flightId, FlightStatus status);

    /**
     * JOIN FETCH flight for batch API; avoids N+1 when booking-service loads many instances.
     */
    @Query("SELECT fi FROM FlightInstance fi JOIN FETCH fi.flight WHERE fi.id IN :ids")
    List<FlightInstance> findAllByIdInWithFlight(@Param("ids") Collection<Long> ids);
}
