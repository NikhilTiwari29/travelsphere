package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.service.FlightScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<ApiResponse<FlightScheduleResponse>> createFlightSchedule(
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
                .body(
                        ApiResponse.success(
                                "Flight schedule created successfully.",
                                response
                        )
                );
    }


    // ==================== Read Operations ====================

    /**
     * Returns a FlightSchedule using its database identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightScheduleResponse>> getFlightScheduleById(
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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight schedule retrieved successfully.",
                        response
                )
        );
    }


    /**
     * Returns all FlightSchedules belonging to the authenticated user's airline.
     *
     * The user ID supplied by the Gateway is used by the service layer
     * to resolve the airline and retrieve its schedule definitions.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FlightScheduleResponse>>> getFlightSchedules(
            @RequestHeader("X-User-Id") Long userId
    ) throws AirportException {

        log.debug(
                "Airline flight schedule lookup request received userId={}",
                userId
        );

        List<FlightScheduleResponse> responses =
                flightScheduleService.getFlightScheduleByAirline(userId);

        log.debug(
                "Airline flight schedule lookup completed userId={} returnedCount={}",
                userId,
                responses.size()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight schedules retrieved successfully.",
                        responses
                )
        );
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing FlightSchedule definition.
     *
     * Schedule validation and persistence are handled by the service layer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightScheduleResponse>> updateFlightSchedule(
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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight schedule updated successfully.",
                        response
                )
        );
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a FlightSchedule using its database identifier.
     *
     * Any validation related to generated FlightInstances or schedule
     * deletion rules is handled by the service layer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlightSchedule(
            @PathVariable Long id
    ) throws AirportException {

        log.info(
                "Flight schedule deletion request received scheduleId={}",
                id
        );

        flightScheduleService.deleteFlightSchedule(id);

        log.info(
                "Flight schedule deleted successfully scheduleId={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight schedule deleted successfully."
                )
        );
    }
}