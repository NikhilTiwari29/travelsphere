package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.AirlineStatus;
import com.nikhil.common_lib.payload.request.AirlineRequest;
import com.nikhil.common_lib.payload.response.AirlineDropdownItem;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.services.service.AirlineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for airline master data and administrative lifecycle operations.
 *
 * Gateway route: /api/airlines/**
 *
 * Supports:
 * - airline registration and owner-scoped management,
 * - airline lookup by owner or ID,
 * - paginated administrative listing,
 * - active-airline dropdown data for downstream consumers,
 * - administrative approval, suspension, and banning.
 *
 * The authenticated user ID is propagated through the X-User-Id header
 * for owner-scoped operations.
 *
 * Downstream consumers include flight operations, ancillary, booking,
 * and seat services. This service does not perform outbound location-service
 * calls; headquartersCityId is stored as an external reference identifier.
 */
@Slf4j
@RestController
@RequestMapping("/api/airlines")
@RequiredArgsConstructor
public class AirlineController {

    private final AirlineService airlineService;


    // ==================== CRUD Operations ====================

    /**
     * Registers a new airline for the authenticated owner.
     */
    @PostMapping
    public ResponseEntity<AirlineResponse> createAirline(
            @Valid @RequestBody AirlineRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.info(
                "Create airline request received userId={} iataCode={}",
                userId,
                request.getIataCode()
        );

        AirlineResponse response =
                airlineService.createAirline(request, userId);

        log.info(
                "Airline created successfully airlineId={} userId={}",
                response.getId(),
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns the airline associated with the authenticated owner.
     *
     * This endpoint is also used by downstream services that need to
     * resolve the airline associated with the propagated user identity.
     */
    @GetMapping("/admin")
    public ResponseEntity<AirlineResponse> getAirlineByOwner(
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Get airline by owner request received userId={}",
                userId
        );

        AirlineResponse response =
                airlineService.getAirlineByOwner(userId);

        return ResponseEntity.ok(response);
    }


    /**
     * Returns an airline by its unique database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AirlineResponse> getAirlineById(
            @PathVariable Long id
    ) {

        log.debug(
                "Get airline request received airlineId={}",
                id
        );

        AirlineResponse response =
                airlineService.getAirlineById(id);

        return ResponseEntity.ok(response);
    }


    /**
     * Returns a paginated list of airlines.
     *
     * Pagination and sorting are resolved automatically from request
     * parameters such as page, size, and sort.
     */
    @GetMapping
    public ResponseEntity<Page<AirlineResponse>> getAllAirlines(
            Pageable pageable
    ) {

        log.debug(
                "Get all airlines request received page={} size={} sort={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        Page<AirlineResponse> response =
                airlineService.getAllAirlines(pageable);

        log.debug(
                "Airline page retrieved page={} returnedElements={} totalElements={}",
                response.getNumber(),
                response.getNumberOfElements(),
                response.getTotalElements()
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns active airlines as lightweight projections for dropdown
     * and selection interfaces.
     */
    @GetMapping("/dropdown")
    public ResponseEntity<List<AirlineDropdownItem>> getAirlinesForDropdown() {

        log.debug(
                "Get airline dropdown request received"
        );

        List<AirlineDropdownItem> response =
                airlineService.getAirlinesForDropdown();

        log.debug(
                "Airline dropdown retrieved count={}",
                response.size()
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Updates the airline associated with the authenticated owner.
     */
    @PutMapping
    public ResponseEntity<AirlineResponse> updateAirline(
            @Valid @RequestBody AirlineRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.info(
                "Update airline request received userId={}",
                userId
        );

        AirlineResponse response =
                airlineService.updateAirline(request, userId);

        log.info(
                "Airline updated successfully airlineId={} userId={}",
                response.getId(),
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an airline after ownership validation in the service layer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAirline(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.info(
                "Delete airline request received airlineId={} userId={}",
                id,
                userId
        );

        airlineService.deleteAirline(id, userId);

        log.info(
                "Airline deleted successfully airlineId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.noContent().build();
    }


    // ==================== Administrative Operations ====================

    /**
     * Activates an airline after administrative approval.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<AirlineResponse> approveAirline(
            @PathVariable Long id
    ) {

        log.info(
                "Airline approval request received airlineId={}",
                id
        );

        AirlineResponse response =
                airlineService.changeStatusByAdmin(
                        id,
                        AirlineStatus.ACTIVE
                );

        log.info(
                "Airline approved successfully airlineId={} status={}",
                id,
                AirlineStatus.ACTIVE
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Suspends an airline by changing its operational status to INACTIVE.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AirlineResponse> suspendAirline(
            @PathVariable Long id
    ) {

        log.info(
                "Airline suspension request received airlineId={}",
                id
        );

        AirlineResponse response =
                airlineService.changeStatusByAdmin(
                        id,
                        AirlineStatus.INACTIVE
                );

        log.info(
                "Airline suspended successfully airlineId={} status={}",
                id,
                AirlineStatus.INACTIVE
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Bans an airline by changing its operational status to BANNED.
     */
    @PostMapping("/{id}/ban")
    public ResponseEntity<AirlineResponse> banAirline(
            @PathVariable Long id
    ) {

        log.info(
                "Airline ban request received airlineId={}",
                id
        );

        AirlineResponse response =
                airlineService.changeStatusByAdmin(
                        id,
                        AirlineStatus.BANNED
                );

        log.info(
                "Airline banned successfully airlineId={} status={}",
                id,
                AirlineStatus.BANNED
        );

        return ResponseEntity.ok(response);
    }
}