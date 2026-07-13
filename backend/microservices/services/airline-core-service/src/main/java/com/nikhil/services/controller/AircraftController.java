package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AircraftRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.services.service.AircraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for aircraft fleet records belonging to airlines.
 *
 * Gateway route: /api/aircrafts/**
 * Authentication: JWT validated through the gateway.
 *
 * The authenticated user ID is propagated through the X-User-Id header
 * and used to scope airline-owned aircraft operations.
 *
 * Aircraft capacity and configuration data is consumed by downstream
 * services such as flight operations, ancillary, and seat services.
 */
@Slf4j
@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftController {

    private final AircraftService aircraftService;

    /**
     * Creates a new aircraft for the airline owned by the authenticated user.
     */
    @PostMapping
    public ResponseEntity<AircraftResponse> createAircraft(
            @RequestBody AircraftRequest request,
            @RequestHeader("X-User-Id") Long userId)
            throws ResourceNotFoundException {

        log.info("Create aircraft request received for userId={}", userId);

        AircraftResponse response =
                aircraftService.createAircraft(request, userId);

        log.info(
                "Aircraft created successfully aircraftId={} userId={}",
                response.getId(),
                userId
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Returns aircraft details by aircraft ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AircraftResponse> getAircraftById(
            @PathVariable Long id)
            throws ResourceNotFoundException {

        log.debug("Get aircraft request received aircraftId={}", id);

        AircraftResponse response =
                aircraftService.getAircraftById(id);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns all aircraft belonging to the airline owned by the
     * authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<AircraftResponse>> listAllAircrafts(
            @RequestHeader("X-User-Id") Long userId) {

        log.debug(
                "List aircraft request received for userId={}",
                userId
        );

        List<AircraftResponse> aircrafts =
                aircraftService.listAllAircraftsByOwner(userId);

        log.debug(
                "Aircraft list retrieved userId={} count={}",
                userId,
                aircrafts.size()
        );

        return ResponseEntity.ok(aircrafts);
    }

    /**
     * Updates an existing aircraft belonging to the authenticated
     * airline owner.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AircraftResponse> updateAircraft(
            @PathVariable Long id,
            @RequestBody AircraftRequest request,
            @RequestHeader("X-User-Id") Long userId)
            throws ResourceNotFoundException {

        log.info(
                "Update aircraft request received aircraftId={} userId={}",
                id,
                userId
        );

        AircraftResponse response =
                aircraftService.updateAircraft(id, request, userId);

        log.info(
                "Aircraft updated successfully aircraftId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes an aircraft by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAircraft(
            @PathVariable Long id)
            throws ResourceNotFoundException {

        log.info(
                "Delete aircraft request received aircraftId={}",
                id
        );

        aircraftService.deleteAircraft(id);

        log.info(
                "Aircraft deleted successfully aircraftId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}