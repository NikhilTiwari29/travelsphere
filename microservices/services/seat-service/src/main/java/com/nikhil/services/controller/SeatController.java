package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.SeatRequest;
import com.nikhil.common_lib.payload.response.SeatResponse;
import com.nikhil.services.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing physical Seat template records belonging to a SeatMap.
 *
 * Seats represent the static physical layout of an aircraft cabin and are
 * generated from SeatMap configuration during seat-map creation.
 *
 * Runtime seat availability and booking state are managed separately through
 * SeatInstance records created for individual flight instances.
 *
 * Domain hierarchy:
 *
 * Aircraft
 *   → CabinClass
 *       → SeatMap
 *           → Seat
 *               → SeatInstance
 *
 * Gateway route:
 * /api/seats/** → seat-service (JWT required)
 */
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;


    // ==================== Read Operations ====================

    /**
     * Returns all physical Seat template records.
     *
     * These records describe static aircraft-layout information such as
     * seat number, row, column, seat type, and seat characteristics.
     */
    @GetMapping
    public ResponseEntity<List<SeatResponse>> getAllSeats() {

        log.debug(
                "Request received to fetch all seat templates"
        );

        List<SeatResponse> responses =
                seatService.getAll();

        log.debug(
                "Seat templates retrieved successfully count={}",
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns a physical Seat template by its unique database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SeatResponse> getSeatById(
            @PathVariable Long id
    ) {

        log.debug(
                "Request received to fetch seat seatId={}",
                id
        );

        SeatResponse response =
                seatService.getSeatById(id);

        log.debug(
                "Seat retrieved successfully seatId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing physical Seat template.
     *
     * This endpoint is intended for modifying seat-level configuration such as
     * seat type, availability flags, emergency-exit status, premium surcharge,
     * extra legroom, accessibility, and onboard amenities.
     *
     * Runtime booking or occupancy state should not be managed here because
     * those states belong to SeatInstance for a specific flight occurrence.
     */
    @PutMapping("/{id}")
    public ResponseEntity<SeatResponse> updateSeat(
            @PathVariable Long id,
            @Valid @RequestBody SeatRequest request
    ) {

        log.info(
                "Seat update request received seatId={} seatNumber={} seatMapId={} cabinClassId={}",
                id,
                request.getSeatNumber(),
                request.getSeatMapId(),
                request.getCabinClassId()
        );

        SeatResponse response =
                seatService.updateSeat(
                        id,
                        request
                );

        log.info(
                "Seat updated successfully seatId={} seatNumber={}",
                id,
                request.getSeatNumber()
        );

        return ResponseEntity.ok(response);
    }
}