package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.services.model.Flight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/*
 * Repository for Flight route definitions.
 *
 * Provides:
 * - Flight lookup by flight number.
 * - Flight-number uniqueness validation.
 * - Bulk duplicate detection.
 * - Airline-specific route filtering.
 * - Status-based filtering.
 * - Administrative keyword search.
 *
 * Flight represents reusable route master data and not a dated
 * FlightInstance occurrence.
 */
public interface FlightRepository extends JpaRepository<Flight, Long> {


    // ==================== Flight Number Operations ====================

    /**
     * Finds a Flight using its unique flight number.
     *
     * Example:
     * 6E201 → corresponding Flight route definition.
     */
    Optional<Flight> findByFlightNumber(
            String flightNumber
    );


    /**
     * Checks whether a Flight with the given flight number already exists.
     *
     * Used during Flight creation to prevent duplicate flight numbers.
     */
    boolean existsByFlightNumber(
            String flightNumber
    );


    /**
     * Checks whether another Flight already uses the given flight number,
     * excluding the current Flight record.
     *
     * Used during update operations so that the existing Flight does not
     * conflict with itself during uniqueness validation.
     */
    boolean existsByFlightNumberAndIdNot(
            String flightNumber,
            Long id
    );


    // ==================== Bulk Validation Operations ====================

    /**
     * Returns only the flight numbers that already exist from the supplied
     * collection of requested flight numbers.
     *
     * Used during bulk Flight creation to detect existing records using
     * one database query instead of calling existsByFlightNumber() for
     * every request.
     *
     * Example:
     *
     * Input:
     * [6E201, AI302, UK955]
     *
     * Existing database records:
     * [6E201, UK955]
     *
     * Result:
     * [6E201, UK955]
     */
    @Query("""
            SELECT f.flightNumber
            FROM Flight f
            WHERE f.flightNumber IN :numbers
            """)
    Set<String> findExistingFlightNumbers(
            @Param("numbers") Collection<String> numbers
    );


    // ==================== Airline Route Operations ====================

    /**
     * Returns paginated Flights belonging to an airline with optional
     * departure and arrival airport filters.
     *
     * Filtering behavior:
     *
     * departureAirportId = null
     *      → departure airport filter is ignored.
     *
     * arrivalAirportId = null
     *      → arrival airport filter is ignored.
     *
     * Both provided:
     *      → returns Flights matching the complete route.
     *
     * Example:
     *
     * airlineId = 1
     * departureAirportId = 10
     * arrivalAirportId = 20
     *
     * Returns Flights operated by airline 1 on route:
     * Airport 10 → Airport 20.
     */
    @Query("""
            SELECT f
            FROM Flight f
            WHERE f.airlineId = :airlineId
              AND (:depId IS NULL OR f.departureAirportId = :depId)
              AND (:arrId IS NULL OR f.arrivalAirportId = :arrId)
            """)
    Page<Flight> findByAirlineIdAndOptionalRoute(
            @Param("airlineId") Long airlineId,
            @Param("depId") Long departureAirportId,
            @Param("arrId") Long arrivalAirportId,
            Pageable pageable
    );


    // ==================== Status Operations ====================

    /**
     * Returns paginated Flights having the requested operational status.
     *
     * Used for status-based administrative filtering.
     */
    Page<Flight> findByStatus(
            FlightStatus status,
            Pageable pageable
    );


    // ==================== Search Operations ====================

    /**
     * Searches Flights using either:
     *
     * 1. Partial flight-number matching.
     * 2. Exact airline ID matching after converting airlineId to String.
     *
     * Examples:
     *
     * keyword = "6E"
     *      → matches flight numbers containing "6E".
     *
     * keyword = "1"
     *      → also matches Flights whose airlineId is exactly 1.
     */
    @Query("""
            SELECT f
            FROM Flight f
            WHERE f.flightNumber LIKE %:keyword%
               OR CAST(f.airlineId AS string) = :keyword
            """)
    Page<Flight> searchByKeyword(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}