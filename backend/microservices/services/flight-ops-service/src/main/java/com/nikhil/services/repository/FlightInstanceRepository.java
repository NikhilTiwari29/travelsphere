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

/*
 * Repository for FlightInstance persistence and search operations.
 *
 * Supports filtered airline queries, route/date searches,
 * status filtering, row locking, and optimized batch loading.
 */
public interface FlightInstanceRepository
        extends JpaRepository<FlightInstance, Long>,
        JpaSpecificationExecutor<FlightInstance> {


    /**
     * Returns paginated flight instances for an airline with optional
     * route, flight, and departure-date filters.
     */
    @Query("""
            SELECT fi FROM FlightInstance fi
            WHERE fi.airlineId = :airlineId
              AND (:departureAirportId IS NULL OR fi.departureAirportId = :departureAirportId)
              AND (:arrivalAirportId IS NULL OR fi.arrivalAirportId = :arrivalAirportId)
              AND (:flightId IS NULL OR fi.flight.id = :flightId)
              AND (:dayStart IS NULL OR fi.departureDateTime >= :dayStart)
              AND (:dayEnd IS NULL OR fi.departureDateTime < :dayEnd)
            """)
    Page<FlightInstance> findByAirlineIdWithFilters(
            @Param("airlineId") Long airlineId,
            @Param("departureAirportId") Long departureAirportId,
            @Param("arrivalAirportId") Long arrivalAirportId,
            @Param("flightId") Long flightId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            Pageable pageable
    );


    /**
     * Returns paginated flight instances having the specified status.
     */
    Page<FlightInstance> findByStatus(
            FlightStatus status,
            Pageable pageable
    );


    /**
     * Returns scheduled flight instances for a route within the
     * requested departure date-time range.
     */
    @Query("""
            SELECT fi FROM FlightInstance fi
            WHERE fi.departureAirportId = :depId
              AND fi.arrivalAirportId = :arrId
              AND fi.departureDateTime >= :fromDate
              AND fi.departureDateTime <= :toDate
              AND fi.status = 'SCHEDULED'
            """)
    List<FlightInstance> searchFlights(
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );


    /**
     * Returns paginated flight instances for a route within the
     * requested departure date-time range.
     */
    @Query("""
            SELECT fi FROM FlightInstance fi
            WHERE fi.departureAirportId = :depId
              AND fi.arrivalAirportId = :arrId
              AND fi.departureDateTime >= :fromDate
              AND fi.departureDateTime <= :toDate
            """)
    Page<FlightInstance> searchFlightsPaged(
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );


    /**
     * Loads a flight instance with a pessimistic write lock for
     * concurrency-sensitive updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT fi FROM FlightInstance fi
            WHERE fi.id = :id
            """)
    Optional<FlightInstance> findByIdForUpdate(
            @Param("id") Long id
    );


    /**
     * Counts flight instances of a flight having the specified status.
     */
    Long countByFlightIdAndStatus(
            Long flightId,
            FlightStatus status
    );


    /**
     * Loads multiple flight instances with their associated Flight
     * entities in one query to avoid additional lazy-loading queries.
     */
    @Query("""
            SELECT fi FROM FlightInstance fi
            JOIN FETCH fi.flight
            WHERE fi.id IN :ids
            """)
    List<FlightInstance> findAllByIdInWithFlight(
            @Param("ids") Collection<Long> ids
    );
}