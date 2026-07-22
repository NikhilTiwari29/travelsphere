package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.common_lib.response.ApiResponse;
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

    @PostMapping
    public ResponseEntity<ApiResponse<FlightInstanceResponse>> createFlightInstance(
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
                .body(
                        ApiResponse.success(
                                "Flight instance created successfully.",
                                response
                        )
                );
    }

    // ==================== Batch Operations ====================

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<Long, FlightInstanceResponse>>> getFlightInstancesByIds(
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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instances retrieved successfully.",
                        responses
                )
        );
    }

    // ==================== Read Operations ====================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightInstanceResponse>> getFlightInstanceById(
            @PathVariable Long id
    ) {

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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instance retrieved successfully.",
                        response
                )
        );
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FlightInstanceResponse>>> getFlightInstances() {

        log.debug(
                "All flight instances lookup request received"
        );

        List<FlightInstanceResponse> responses =
                flightInstanceService.getFlightInstances();

        log.debug(
                "All flight instances lookup completed returnedCount={}",
                responses.size()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instances retrieved successfully.",
                        responses
                )
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FlightInstanceResponse>>> getByAirlineId(
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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instances retrieved successfully.",
                        responses
                )
        );
    }

    // ==================== Update Operations ====================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightInstanceResponse>> updateFlightInstance(
            @PathVariable Long id,
            @Valid @RequestBody FlightInstanceRequest request
    ) {

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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instance updated successfully.",
                        response
                )
        );
    }

    // ==================== Delete Operations ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlightInstance(
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

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Flight instance deleted successfully."
                )
        );
    }
}