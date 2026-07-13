package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.payload.request.SeatInstanceRequest;
import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import com.nikhil.services.service.SeatInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * REST API for runtime seat inventory, availability, and seat-level pricing.
 *
 * A SeatInstance represents one physical Seat for a specific flight occurrence.
 * Unlike Seat, which defines the static aircraft layout, SeatInstance maintains
 * runtime state such as availability, booking status, and premium surcharge.
 *
 * Request Flow
 * ------------
 * Client / Booking Service
 *      → SeatInstanceController
 *      → SeatInstanceService
 *      → SeatInstanceRepository
 *
 * Gateway route:
 * /api/seat-instances/** → seat-service
 *
 * Main consumers:
 * - Booking Service: seat lookup and surcharge calculation.
 * - Seat-selection UI: available-seat listing and availability counts.
 * - Booking event processing: runtime seat status updates.
 */
@Slf4j
@RestController
@RequestMapping("/api/seat-instances")
@RequiredArgsConstructor
public class SeatInstanceController {

    private final SeatInstanceService seatInstanceService;


    // ==================== Create Operations ====================

    /**
     * Creates a runtime SeatInstance from the supplied seat and flight details.
     *
     * SeatInstances are normally provisioned from Seat templates when flight
     * cabin inventory is created. This endpoint supports explicit creation
     * when required by administrative or internal workflows.
     */
    @PostMapping
    public ResponseEntity<SeatInstanceResponse> createSeatInstance(
            @Valid @RequestBody SeatInstanceRequest request
    ) {

        log.info(
                "Seat instance creation request received flightId={} flightInstanceId={} seatId={}",
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getSeatId()
        );

        SeatInstanceResponse response =
                seatInstanceService.createSeatInstance(request);

        log.info(
                "Seat instance created successfully seatInstanceId={} flightId={} flightInstanceId={}",
                response.getId(),
                request.getFlightId(),
                request.getFlightInstanceId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a runtime seat instance by its database identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SeatInstanceResponse> getSeatInstanceById(
            @PathVariable Long id
    ) {

        log.debug(
                "Request received to fetch seat instance seatInstanceId={}",
                id
        );

        SeatInstanceResponse response =
                seatInstanceService.getSeatInstanceById(id);

        log.debug(
                "Seat instance retrieved successfully seatInstanceId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Calculates the combined seat-selection surcharge for the requested
     * SeatInstances.
     *
     * Booking Service uses this endpoint during checkout to calculate the
     * additional charge for selected WINDOW, AISLE, or other premium seats.
     */
    @PostMapping("/price/total")
    public ResponseEntity<Double> calculateSeatPrice(
            @RequestBody List<Long> seatInstanceIds
    ) {

        log.debug(
                "Seat price calculation request received seatCount={}",
                seatInstanceIds.size()
        );

        Double totalPrice =
                seatInstanceService.calculateSeatPrice(seatInstanceIds);

        log.debug(
                "Seat price calculation completed seatCount={} totalPrice={}",
                seatInstanceIds.size(),
                totalPrice
        );

        return ResponseEntity.ok(totalPrice);
    }


    /**
     * Returns all runtime SeatInstances associated with the specified flight.
     *
     * The result may include seats in different runtime states such as
     * AVAILABLE, HELD, or BOOKED.
     */
    @GetMapping("/flight/{flightId}")
    public ResponseEntity<List<SeatInstanceResponse>> getSeatInstancesByFlightId(
            @PathVariable Long flightId
    ) {

        log.debug(
                "Request received to fetch seat instances flightId={}",
                flightId
        );

        List<SeatInstanceResponse> responses =
                seatInstanceService.getSeatInstancesByFlightId(flightId);

        log.debug(
                "Seat instances retrieved flightId={} count={}",
                flightId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns multiple SeatInstances using their database identifiers.
     *
     * Booking Service uses this batch endpoint to retrieve seat details
     * without making one network request for each selected seat.
     */
    @GetMapping("/all")
    public ResponseEntity<List<SeatInstanceResponse>> getAllByIds(
            @RequestParam List<Long> Ids
    ) {

        log.debug(
                "Batch seat instance lookup request received requestedCount={}",
                Ids.size()
        );

        List<SeatInstanceResponse> responses =
                seatInstanceService.getAllByIds(Ids);

        log.debug(
                "Batch seat instance lookup completed requestedCount={} returnedCount={}",
                Ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns only currently available SeatInstances for the specified flight.
     *
     * Primarily used by the seat-selection UI during the booking flow.
     */
    @GetMapping("/flight/{flightId}/available")
    public ResponseEntity<List<SeatInstanceResponse>> getAvailableSeatsByFlightId(
            @PathVariable Long flightId
    ) {

        log.debug(
                "Request received to fetch available seats flightId={}",
                flightId
        );

        List<SeatInstanceResponse> responses =
                seatInstanceService.getAvailableSeatsByFlightId(flightId);

        log.debug(
                "Available seats retrieved flightId={} availableCount={}",
                flightId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns the number of currently available SeatInstances for a flight.
     *
     * Useful for lightweight availability checks where full seat details
     * are not required.
     */
    @GetMapping("/flight/{flightId}/available/count")
    public ResponseEntity<Long> countAvailableByFlightId(
            @PathVariable Long flightId
    ) {

        log.debug(
                "Request received to count available seats flightId={}",
                flightId
        );

        Long availableCount =
                seatInstanceService.countAvailableByFlightId(flightId);

        log.debug(
                "Available seat count retrieved flightId={} availableCount={}",
                flightId,
                availableCount
        );

        return ResponseEntity.ok(availableCount);
    }


    // ==================== Status Operations ====================

    /**
     * Updates the runtime availability status of a SeatInstance.
     *
     * Typical transitions include:
     *
     * AVAILABLE → HELD
     * HELD → BOOKED
     * HELD → AVAILABLE
     *
     * Seat status may also be updated indirectly through booking-related
     * event consumers.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<SeatInstanceResponse> updateSeatInstanceStatus(
            @PathVariable Long id,
            @RequestParam SeatAvailabilityStatus status
    ) {

        log.info(
                "Seat instance status update requested seatInstanceId={} targetStatus={}",
                id,
                status
        );

        SeatInstanceResponse response =
                seatInstanceService.updateSeatInstanceStatus(
                        id,
                        status
                );

        log.info(
                "Seat instance status updated successfully seatInstanceId={} status={}",
                id,
                status
        );

        return ResponseEntity.ok(response);
    }
}