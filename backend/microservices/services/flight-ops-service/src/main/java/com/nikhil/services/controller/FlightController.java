package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightRequest;
import com.nikhil.common_lib.payload.response.FlightResponse;
import com.nikhil.services.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * REST API for managing Flight route definitions.
 *
 * A Flight represents the reusable route definition operated by an airline,
 * not a specific dated departure.
 *
 * Example:
 *
 * Flight
 *   6E-201: DEL → BOM
 *
 * FlightInstances
 *   6E-201 on 2026-07-10
 *   6E-201 on 2026-07-11
 *   6E-201 on 2026-07-12
 *
 * Request Flow
 * ------------
 * Client / Booking Service
 *          ↓
 * FlightController
 *          ↓
 * FlightService
 *          ↓
 * FlightRepository
 *
 * Gateway route:
 * /api/flights/** → flight-ops-service
 *
 * Cross-service references such as airline, aircraft, and airport IDs
 * identify resources owned by their respective microservices.
 */
@Slf4j
@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;


    // ==================== Create Operations ====================

    /**
     * Creates a Flight route definition for the authenticated airline owner.
     *
     * The authenticated user's ID is forwarded by the Gateway through
     * X-User-Id and is used by the service layer to resolve the airline.
     */
    @PostMapping
    public ResponseEntity<FlightResponse> createFlight(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightRequest request
    ) throws AirportException {

        log.info(
                "Flight creation request received userId={} flightNumber={} departureAirportId={} arrivalAirportId={} aircraftId={}",
                userId,
                request.getFlightNumber(),
                request.getDepartureAirportId(),
                request.getArrivalAirportId(),
                request.getAircraftId()
        );

        FlightResponse response =
                flightService.createFlight(
                        userId,
                        request
                );

        log.info(
                "Flight created successfully flightId={} flightNumber={} userId={}",
                response.getId(),
                request.getFlightNumber(),
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple Flight route definitions for the authenticated
     * airline owner in a single request.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<FlightResponse>> createFlights(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody List<FlightRequest> requests
    ) throws AirportException {

        log.info(
                "Bulk flight creation request received userId={} requestedCount={}",
                userId,
                requests.size()
        );

        List<FlightResponse> responses =
                flightService.createFlights(
                        userId,
                        requests
                );

        log.info(
                "Bulk flight creation completed userId={} requestedCount={} createdCount={}",
                userId,
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    // ==================== Batch Operations ====================

    /**
     * Returns multiple Flights using their database identifiers.
     *
     * Booking Service uses this batch endpoint to enrich booking details
     * without making one network request for every Flight.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<Long, FlightResponse>> getFlightsByIds(
            @RequestBody List<Long> ids
    ) {

        log.debug(
                "Batch flight lookup request received requestedCount={}",
                ids.size()
        );

        Map<Long, FlightResponse> responses =
                flightService.getFlightsByIds(ids);

        log.debug(
                "Batch flight lookup completed requestedCount={} returnedCount={}",
                ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a Flight route definition by its database identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightResponse> getFlightById(
            @PathVariable Long id
    ) {

        log.debug(
                "Request received to fetch flight flightId={}",
                id
        );

        FlightResponse response =
                flightService.getFlightById(id);

        log.debug(
                "Flight retrieved successfully flightId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns a Flight route definition using its airline flight number.
     *
     * Example:
     * 6E201, AI302, UK955.
     */
    @GetMapping("/number/{flightNumber}")
    public ResponseEntity<FlightResponse> getFlightByNumber(
            @PathVariable String flightNumber
    ) throws AirportException {

        log.debug(
                "Request received to fetch flight flightNumber={}",
                flightNumber
        );

        FlightResponse response =
                flightService.getFlightByNumber(flightNumber);

        log.debug(
                "Flight retrieved successfully flightNumber={}",
                flightNumber
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns paginated Flights belonging to the authenticated user's airline.
     *
     * Optional departure and arrival airport filters can be supplied to
     * narrow the result to specific routes.
     */
    @GetMapping("/airline")
    public ResponseEntity<Page<FlightResponse>> getFlightsByAirline(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            Pageable pageable
    ) {

        log.debug(
                "Airline flight search request received userId={} departureAirportId={} arrivalAirportId={} page={} size={}",
                userId,
                departureAirportId,
                arrivalAirportId,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<FlightResponse> responses =
                flightService.getFlightsByAirline(
                        userId,
                        departureAirportId,
                        arrivalAirportId,
                        pageable
                );

        log.debug(
                "Airline flight search completed userId={} returnedCount={} totalElements={}",
                userId,
                responses.getNumberOfElements(),
                responses.getTotalElements()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing Flight route definition.
     *
     * Mutable route configuration is validated and updated by the
     * Flight Service.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightResponse> updateFlight(
            @PathVariable Long id,
            @Valid @RequestBody FlightRequest request
    ) throws AirportException {

        log.info(
                "Flight update request received flightId={} flightNumber={}",
                id,
                request.getFlightNumber()
        );

        FlightResponse response =
                flightService.updateFlight(
                        id,
                        request
                );

        log.info(
                "Flight updated successfully flightId={} flightNumber={}",
                id,
                request.getFlightNumber()
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Status Operations ====================

    /**
     * Changes the operational status of a Flight route definition.
     *
     * This controls whether the Flight is active, suspended, cancelled,
     * or in another state supported by FlightStatus.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<FlightResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam FlightStatus status
    ) throws AirportException {

        log.info(
                "Flight status change requested flightId={} targetStatus={}",
                id,
                status
        );

        FlightResponse response =
                flightService.changeStatus(
                        id,
                        status
                );

        log.info(
                "Flight status changed successfully flightId={} status={}",
                id,
                status
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a Flight route definition by its database identifier.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlight(
            @PathVariable Long id
    ) {

        log.info(
                "Flight deletion request received flightId={}",
                id
        );

        flightService.deleteFlight(id);

        log.info(
                "Flight deleted successfully flightId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}