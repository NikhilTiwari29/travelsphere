package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.SeatMapRequest;
import com.nikhil.common_lib.payload.response.SeatMapResponse;
import com.nikhil.services.service.SeatMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for aircraft seat-map layout management.
 *
 * A SeatMap defines the physical seating layout associated with a CabinClass.
 * The configured layout is used by the service layer to generate and manage
 * physical Seat records for the aircraft.
 *
 * Domain hierarchy:
 *
 * Aircraft
 *   → CabinClass
 *       → SeatMap
 *           → Seat
 *               → SeatInstance (per flight instance)
 *
 * Gateway route: /api/seat-maps/**
 *
 * Create and update operations use the X-User-Id header propagated by the
 * Gateway after JWT authentication. The service layer uses this identity
 * to resolve the airline and validate ownership of the related resources.
 */
@Slf4j
@RestController
@RequestMapping("/api/seat-maps")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapService seatMapService;


    // ==================== Create Operations ====================

    /**
     * Creates a seat-map layout for a cabin class.
     *
     * The service layer creates the layout and generates the corresponding
     * physical Seat records based on the configured row and seat arrangement.
     *
     * @param userId  authenticated user ID propagated by the Gateway
     * @param request seat-map layout configuration
     * @return the created seat-map representation
     */
    @PostMapping
    public ResponseEntity<SeatMapResponse> createSeatMap(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SeatMapRequest request
    ) throws Exception {

        log.info(
                "Create seat map request received userId={} cabinClassId={}",
                userId,
                request.getCabinClassId()
        );

        SeatMapResponse response =
                seatMapService.createSeatMap(userId, request);

        log.info(
                "Seat map created successfully seatMapId={} cabinClassId={} userId={}",
                response.getId(),
                request.getCabinClassId(),
                userId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple seat-map layouts in a single request.
     *
     * Typically used while configuring seat layouts for multiple cabin classes
     * belonging to an aircraft.
     *
     * @param userId  authenticated user ID propagated by the Gateway
     * @param requests seat-map layout configurations
     * @return list of created seat maps
     */
    @PostMapping("/create/bulk")
    public ResponseEntity<List<SeatMapResponse>> createSeatMaps(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody List<SeatMapRequest> requests
    ) throws Exception {

        log.info(
                "Bulk seat map creation request received userId={} requestedCount={}",
                userId,
                requests.size()
        );

        List<SeatMapResponse> responses =
                seatMapService.createSeatMaps(userId, requests);

        log.info(
                "Bulk seat map creation completed userId={} requestedCount={} createdCount={}",
                userId,
                requests.size(),
                responses.size()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responses);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a seat map by its unique database ID.
     *
     * @param id seat-map ID
     * @return matching seat-map representation
     */
    @GetMapping("/{id}")
    public ResponseEntity<SeatMapResponse> getSeatMapById(
            @PathVariable Long id
    ) {

        log.debug(
                "Get seat map request received seatMapId={}",
                id
        );

        SeatMapResponse response =
                seatMapService.getSeatMapById(id);

        log.debug(
                "Seat map retrieved successfully seatMapId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns the seat map associated with the specified cabin class.
     *
     * Each cabin class has a seat-map layout that defines its row boundaries,
     * seat arrangement, and other physical layout characteristics.
     *
     * @param cabinClassId cabin-class ID
     * @return seat map associated with the cabin class
     */
    @GetMapping("/cabin-class/{cabinClassId}")
    public ResponseEntity<SeatMapResponse> getSeatMapsByCabinClass(
            @PathVariable Long cabinClassId
    ) {

        log.debug(
                "Get seat map by cabin class request received cabinClassId={}",
                cabinClassId
        );

        SeatMapResponse responses =
                seatMapService.getSeatMapsByCabinClass(cabinClassId);

        log.debug(
                "Seat map retrieved for cabinClassId={}",
                cabinClassId
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing seat-map layout.
     *
     * The authenticated user ID is passed to the service layer for airline
     * resolution and ownership validation.
     *
     * @param userId authenticated user ID propagated by the Gateway
     * @param id seat-map ID
     * @param request updated seat-map configuration
     * @return updated seat-map representation
     */
    @PutMapping("/{id}")
    public ResponseEntity<SeatMapResponse> updateSeatMap(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SeatMapRequest request
    ) {

        log.info(
                "Update seat map request received seatMapId={} userId={}",
                id,
                userId
        );

        SeatMapResponse response =
                seatMapService.updateSeatMap(userId, id, request);

        log.info(
                "Seat map updated successfully seatMapId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a seat map by its unique database ID.
     *
     * The service layer handles deletion of the seat map and any associated
     * cleanup required by the domain model.
     *
     * @param id seat-map ID
     * @return success response confirming deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSeatMap(
            @PathVariable Long id
    ) throws Exception {

        log.info(
                "Delete seat map request received seatMapId={}",
                id
        );

        seatMapService.deleteSeatMap(id);

        log.info(
                "Seat map deleted successfully seatMapId={}",
                id
        );

        return ResponseEntity.ok("Seat map deleted");
    }
}