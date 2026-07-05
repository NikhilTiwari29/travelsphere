package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.CabinClassRequest;
import com.nikhil.common_lib.payload.response.CabinClassResponse;
import com.nikhil.services.service.CabinClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for cabin-class definitions associated with aircraft layouts.
 *
 * Gateway route: /api/cabin-classes/**
 *
 * Cabin classes define the top-level seat hierarchy:
 *
 * CabinClass → SeatMap → Seat → SeatInstance
 *
 * The API supports cabin-class creation, bulk creation, lookup by ID,
 * lookup by aircraft and cabin-class type, aircraft-level listing,
 * update, and deletion.
 *
 * Flight Operations Service uses aircraft and cabin-class lookups through
 * Feign when resolving cabin configuration during flight search and pricing.
 */
@Slf4j
@RestController
@RequestMapping("/api/cabin-classes")
@RequiredArgsConstructor
public class CabinClassController {

    private final CabinClassService cabinClassService;


    // ==================== Create Operations ====================

    /**
     * Creates a cabin-class definition for an aircraft layout.
     */
    @PostMapping
    public ResponseEntity<CabinClassResponse> createCabinClass(
            @Valid @RequestBody CabinClassRequest request
    ) {

        log.info(
                "Create cabin class request received aircraftId={}",
                request.getAircraftId()
        );

        CabinClassResponse response =
                cabinClassService.createCabinClass(request);

        log.info(
                "Cabin class created successfully cabinClassId={} aircraftId={}",
                response.getId(),
                request.getAircraftId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple cabin-class definitions in a single request.
     *
     * Used when configuring the complete cabin layout of an aircraft.
     */
    @PostMapping("/create/bulk")
    public ResponseEntity<List<CabinClassResponse>> createCabinClasses(
            @Valid @RequestBody List<CabinClassRequest> requests
    ) {

        log.info(
                "Bulk cabin class creation request received count={}",
                requests.size()
        );

        List<CabinClassResponse> responses =
                cabinClassService.createCabinClasses(requests);

        log.info(
                "Bulk cabin class creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a cabin class by its unique database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CabinClassResponse> getCabinClassById(
            @PathVariable Long id
    ) {

        log.debug(
                "Get cabin class request received cabinClassId={}",
                id
        );

        CabinClassResponse response =
                cabinClassService.getCabinClassById(id);

        return ResponseEntity.ok(response);
    }


    /**
     * Resolves a cabin class by aircraft ID and cabin-class type.
     *
     * Used by Flight Operations Service before downstream pricing
     * and availability processing during flight search.
     */
    @GetMapping("/aircraft/{id}/name/{cabinClass}")
    public ResponseEntity<CabinClassResponse>
    getCabinClassByAircraftIdAndName(
            @PathVariable Long id,
            @PathVariable CabinClassType cabinClass
    ) {

        log.debug(
                "Get cabin class by aircraft request received aircraftId={} cabinClass={}",
                id,
                cabinClass
        );

        CabinClassResponse response =
                cabinClassService.getByAircraftIdAndName(
                        id,
                        cabinClass
                );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns all cabin classes configured for an aircraft.
     *
     * Used during aircraft layout configuration and flight search setup.
     */
    @GetMapping("/aircraft/{aircraftId}")
    public ResponseEntity<List<CabinClassResponse>>
    getCabinClassesByAircraftId(
            @PathVariable Long aircraftId
    ) {

        log.debug(
                "Get cabin classes request received aircraftId={}",
                aircraftId
        );

        List<CabinClassResponse> responses =
                cabinClassService.getCabinClassesByAircraftId(
                        aircraftId
                );

        log.debug(
                "Cabin classes retrieved aircraftId={} count={}",
                aircraftId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing cabin-class configuration.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CabinClassResponse> updateCabinClass(
            @PathVariable Long id,
            @Valid @RequestBody CabinClassRequest request
    ) {

        log.info(
                "Update cabin class request received cabinClassId={}",
                id
        );

        CabinClassResponse response =
                cabinClassService.updateCabinClass(
                        id,
                        request
                );

        log.info(
                "Cabin class updated successfully cabinClassId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a cabin-class definition by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinClass(
            @PathVariable Long id
    ) {

        log.info(
                "Delete cabin class request received cabinClassId={}",
                id
        );

        cabinClassService.deleteCabinClass(id);

        log.info(
                "Cabin class deleted successfully cabinClassId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}