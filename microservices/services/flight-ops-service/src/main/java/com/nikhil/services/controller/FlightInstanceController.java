package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.service.FlightInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/*
 * REST API for managing concrete, dated FlightInstance records.
 *
 * A Flight defines the reusable route configuration, while a FlightInstance
 * represents one actual occurrence of that Flight on a specific date and time.
 *
 * Example:
 *
 * Flight:
 *   6E201 → DEL to BOM
 *
 * FlightInstances:
 *   6E201 → 2026-07-10 10:00
 *   6E201 → 2026-07-12 10:00
 *   6E201 → 2026-07-14 10:00
 *
 * Request Flow
 * ------------
 * FlightInstanceController
 *          ↓
 * FlightInstanceService
 *          ↓
 * Persist FlightInstance
 *          ↓
 * Provision runtime cabin inventory
 *          ↓
 * Create FlightInstanceCabin and SeatInstance records
 *
 * FlightInstances may be created:
 *
 * 1. Automatically when a recurring FlightSchedule is expanded.
 * 2. Manually through POST /api/flight-instances.
 *
 * Gateway route:
 * /api/flight-instances/** → flight-ops-service
 *
 * Cross-service communication:
 *
 * Airline Core Service
 *   - Resolve airline ownership.
 *   - Retrieve Airline and Aircraft information.
 *
 * Location Service
 *   - Retrieve departure and arrival Airport information.
 *
 * Booking Service
 *   - Uses batch lookup to retrieve multiple FlightInstances efficiently.
 */
@Slf4j
@RestController
@RequestMapping("/api/flight-instances")
@RequiredArgsConstructor
public class FlightInstanceController {

    private final FlightInstanceService flightInstanceService;


    // ==================== Create Operations ====================

    /**
     * Creates one concrete FlightInstance.
     *
     * A FlightInstance represents one actual occurrence of a Flight
     * at a specific departure and arrival date-time.
     *
     * Example:
     *
     * Flight:
     *   6E201 → DEL to BOM
     *
     * FlightInstance:
     *   6E201
     *   Departure → 2026-07-10 10:00
     *   Arrival   → 2026-07-10 12:30
     *
     * The service layer also handles the related runtime cabin and
     * seat-inventory provisioning flow.
     */
    @PostMapping
    public ResponseEntity<FlightInstanceResponse> createFlightInstance(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightInstanceRequest request
    ) throws Exception {

        log.info(
                "Flight instance creation request received userId={} flightId={} scheduleId={} departureDateTime={}",
                userId,
                request.getFlightId(),
                request.getScheduleId(),
                request.getDepartureDateTime()
        );

        FlightInstanceResponse response =
                flightInstanceService.createFlightInstanceWithCabins(
                        userId,
                        request
                );

        log.info(
                "Flight instance created successfully flightInstanceId={} flightId={} userId={}",
                response.getId(),
                request.getFlightId(),
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // ==================== Batch Operations ====================

    /**
     * Returns multiple FlightInstances using their database identifiers.
     *
     * Booking Service uses this endpoint to retrieve several flight-instance
     * records in one network request instead of calling the service separately
     * for every FlightInstance.
     *
     * Example request:
     *
     * [101, 102, 103]
     *
     * Response:
     *
     * {
     *   101: FlightInstanceResponse,
     *   102: FlightInstanceResponse,
     *   103: FlightInstanceResponse
     * }
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<Long, FlightInstanceResponse>>
    getFlightInstancesByIds(
            @RequestBody List<Long> ids
    ) {

        log.debug(
                "Batch flight instance lookup request received requestedCount={}",
                ids.size()
        );

        Map<Long, FlightInstanceResponse> responses =
                flightInstanceService.getFlightInstancesByIds(ids);

        log.debug(
                "Batch flight instance lookup completed requestedCount={} returnedCount={}",
                ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Read Operations ====================

    /**
     * Returns one FlightInstance using its database identifier.
     *
     * The returned response may contain enriched Flight, Airline,
     * Aircraft, and Airport information depending on the service mapping.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightInstanceResponse> getFlightInstanceById(
            @PathVariable Long id
    ) throws AirportException {

        log.debug(
                "Flight instance lookup request received flightInstanceId={}",
                id
        );

        FlightInstanceResponse response =
                flightInstanceService.getFlightInstanceById(id);

        log.debug(
                "Flight instance lookup completed flightInstanceId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns all FlightInstance records.
     *
     * This endpoint performs an unrestricted list operation and should mainly
     * be used for internal administration or development purposes. For normal
     * airline-specific queries, use the paginated filtered endpoint.
     */
    @GetMapping("/list")
    public ResponseEntity<List<FlightInstanceResponse>>
    getFlightInstances() throws AirportException {

        log.debug(
                "All flight instances lookup request received"
        );

        List<FlightInstanceResponse> responses =
                flightInstanceService.getFlightInstances();

        log.debug(
                "All flight instances lookup completed returnedCount={}",
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns paginated FlightInstances belonging to the authenticated
     * user's airline.
     *
     * Optional filters can narrow the result by:
     *
     * - departure airport
     * - arrival airport
     * - Flight ID
     * - operating date
     *
     * Examples:
     *
     * All airline instances:
     *   GET /api/flight-instances
     *
     * Route filter:
     *   GET /api/flight-instances
     *       ?departureAirportId=1
     *       &arrivalAirportId=2
     *
     * Flight filter:
     *   GET /api/flight-instances?flightId=10
     *
     * Date filter:
     *   GET /api/flight-instances?onDate=2026-07-10
     */
    @GetMapping
    public ResponseEntity<Page<FlightInstanceResponse>> getByAirlineId(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            @RequestParam(required = false) Long flightId,
            @RequestParam(required = false) LocalDate onDate,
            Pageable pageable
    ) {

        log.debug(
                "Airline flight instance lookup request received userId={} departureAirportId={} arrivalAirportId={} flightId={} onDate={} page={} size={}",
                userId,
                departureAirportId,
                arrivalAirportId,
                flightId,
                onDate,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<FlightInstanceResponse> responses =
                flightInstanceService.getByAirlineId(
                        userId,
                        departureAirportId,
                        arrivalAirportId,
                        flightId,
                        onDate,
                        pageable
                );

        log.debug(
                "Airline flight instance lookup completed userId={} returnedCount={} totalElements={} totalPages={}",
                userId,
                responses.getNumberOfElements(),
                responses.getTotalElements(),
                responses.getTotalPages()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing FlightInstance.
     *
     * The service layer validates the target FlightInstance and applies
     * the requested runtime flight changes.
     *
     * Changes to flight timing or operational data should be handled
     * carefully because the FlightInstance may already have related
     * cabin inventory, SeatInstances, or booking references.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightInstanceResponse> updateFlightInstance(
            @PathVariable Long id,
            @Valid @RequestBody FlightInstanceRequest request
    ) throws AirportException {

        log.info(
                "Flight instance update request received flightInstanceId={} flightId={} departureDateTime={}",
                id,
                request.getFlightId(),
                request.getDepartureDateTime()
        );

        FlightInstanceResponse response =
                flightInstanceService.updateFlightInstance(
                        id,
                        request
                );

        log.info(
                "Flight instance updated successfully flightInstanceId={} flightId={}",
                id,
                request.getFlightId()
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a FlightInstance using its database identifier.
     *
     * The service layer is responsible for validating whether deletion is
     * allowed when related cabin inventory, SeatInstances, or booking data
     * already exist.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightInstance(
            @PathVariable Long id
    ) {

        log.info(
                "Flight instance deletion request received flightInstanceId={}",
                id
        );

        flightInstanceService.deleteFlightInstance(id);

        log.info(
                "Flight instance deleted successfully flightInstanceId={}",
                id
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}