package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import com.nikhil.services.service.FlightScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * REST API for managing recurring FlightSchedule definitions.
 *
 * A FlightSchedule defines when a Flight operates over a date range
 * and on which days of the week it should run.
 *
 * Example:
 *
 * Flight:
 *   6E201 → DEL to BOM
 *
 * FlightSchedule:
 *   2026-07-01 to 2026-07-31
 *   Operating days → MONDAY, WEDNESDAY, FRIDAY
 *
 * Generated FlightInstances:
 *   6E201 → 2026-07-01
 *   6E201 → 2026-07-03
 *   6E201 → 2026-07-06
 *   ...
 *
 * Request Flow
 * ------------
 * FlightScheduleController
 *          ↓
 * FlightScheduleService
 *          ↓
 * Save FlightSchedule
 *          ↓
 * Generate FlightInstances for operating dates
 *          ↓
 * Publish flight-instance-created Kafka events
 *          ↓
 * Seat Service provisions runtime cabin and seat inventory
 *
 * Gateway route:
 * /api/flight-schedules/** → flight-ops-service
 */
@Slf4j
@RestController
@RequestMapping("/api/flight-schedules")
@RequiredArgsConstructor
public class FlightScheduleController {

    private final FlightScheduleService flightScheduleService;


    // ==================== Create Operations ====================

    /**
     * Creates a recurring FlightSchedule for the authenticated airline.
     *
     * The service persists the schedule and generates FlightInstance records
     * for all valid operating dates defined by the schedule.
     *
     * Each generated FlightInstance can trigger downstream seat-inventory
     * provisioning through Kafka.
     */
    @PostMapping
    public ResponseEntity<FlightScheduleResponse> createFlightSchedule(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightScheduleRequest request
    ) throws Exception {

        log.info(
                "Flight schedule creation request received userId={} flightId={}",
                userId,
                request.getFlightId()
        );

        FlightScheduleResponse response =
                flightScheduleService.createFlightSchedule(
                        userId,
                        request
                );

        log.info(
                "Flight schedule created successfully scheduleId={} flightId={} userId={}",
                response.getId(),
                request.getFlightId(),
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a FlightSchedule using its database identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightScheduleResponse> getFlightScheduleById(
            @PathVariable Long id
    ) throws AirportException {

        log.debug(
                "Flight schedule lookup request received scheduleId={}",
                id
        );

        FlightScheduleResponse response =
                flightScheduleService.getFlightScheduleById(id);

        log.debug(
                "Flight schedule lookup completed scheduleId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns all FlightSchedules belonging to the authenticated user's airline.
     *
     * The user ID supplied by the Gateway is used by the service layer
     * to resolve the airline and retrieve its schedule definitions.
     */
    @GetMapping
    public ResponseEntity<?> getFlightSchedules(
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Airline flight schedule lookup request received userId={}",
                userId
        );

        var responses =
                flightScheduleService.getFlightScheduleByAirline(userId);

        log.debug(
                "Airline flight schedule lookup completed userId={}",
                userId
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing FlightSchedule definition.
     *
     * Schedule validation and persistence are handled by the service layer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightScheduleResponse> updateFlightSchedule(
            @PathVariable Long id,
            @Valid @RequestBody FlightScheduleRequest request
    ) throws AirportException {

        log.info(
                "Flight schedule update request received scheduleId={} flightId={}",
                id,
                request.getFlightId()
        );

        FlightScheduleResponse response =
                flightScheduleService.updateFlightSchedule(
                        id,
                        request
                );

        log.info(
                "Flight schedule updated successfully scheduleId={} flightId={}",
                id,
                request.getFlightId()
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a FlightSchedule using its database identifier.
     *
     * Any validation related to generated FlightInstances or schedule
     * deletion rules is handled by the service layer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightSchedule(
            @PathVariable Long id
    ) {

        log.info(
                "Flight schedule deletion request received scheduleId={}",
                id
        );

        flightScheduleService.deleteFlightSchedule(id);

        log.info(
                "Flight schedule deleted successfully scheduleId={}",
                id
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}