package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.FlightInstanceCabinRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceCabinResponse;
import com.nikhil.services.service.FlightInstanceCabinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for managing cabin-level seat inventory for flight instances.
 *
 * A FlightInstanceCabin represents one cabin class, such as ECONOMY,
 * BUSINESS, or FIRST, for a specific flight occurrence.
 *
 * Domain hierarchy:
 *
 * FlightInstance
 *      → FlightInstanceCabin
 *          → SeatInstance
 *
 * Provisioning hierarchy:
 *
 * CabinClass
 *      → SeatMap
 *          → Seat templates
 *              → SeatInstances for a flight occurrence
 *
 * Gateway route:
 * /api/flight-instance-cabins/** → seat-service (JWT required)
 *
 * Cabin inventory may be provisioned manually through this API or
 * asynchronously from a flight-instance-created Kafka event.
 */
@Slf4j
@RestController
@RequestMapping("/api/flight-instance-cabins")
@RequiredArgsConstructor
public class FlightInstanceCabinController {

    private final FlightInstanceCabinService flightInstanceCabinService;


    // ==================== Create Operations ====================

    /**
     * Manually provisions cabin inventory for a flight instance.
     *
     * The service resolves the CabinClass and its SeatMap, creates the
     * FlightInstanceCabin record, and generates SeatInstance records from
     * the physical Seat templates.
     *
     * The Kafka-based provisioning flow is normally used when new flight
     * instances are created automatically.
     */
    @PostMapping
    public ResponseEntity<FlightInstanceCabinResponse> createFlightInstanceCabin(
            @Valid @RequestBody FlightInstanceCabinRequest request
    ) throws Exception {

        log.info(
                "Flight instance cabin creation request received flightId={} flightInstanceId={} cabinClassId={}",
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getCabinClassId()
        );

        FlightInstanceCabinResponse response =
                flightInstanceCabinService.createFlightInstanceCabin(
                        request
                );

        log.info(
                "Flight instance cabin creation completed flightInstanceId={} cabinClassId={}",
                request.getFlightInstanceId(),
                request.getCabinClassId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a FlightInstanceCabin by its unique database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightInstanceCabinResponse> getFlightInstanceCabinById(
            @PathVariable Long id
    ) {

        log.debug(
                "Request received to fetch flight instance cabin cabinId={}",
                id
        );

        FlightInstanceCabinResponse response =
                flightInstanceCabinService
                        .getFlightInstanceCabinById(id);

        log.debug(
                "Flight instance cabin retrieved successfully cabinId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns the cabin inventory bucket for a specific combination of
     * flight instance and cabin class.
     *
     * Example:
     *
     * FlightInstance 1001 + BUSINESS CabinClass
     *      → corresponding FlightInstanceCabin
     */
    @GetMapping(
            "/flight-instance/{flightInstanceId}/cabin-class/{cabinClassId}"
    )
    public ResponseEntity<?> getByFlightInstanceIdAndCabinClassId(
            @PathVariable Long cabinClassId,
            @PathVariable Long flightInstanceId
    ) {

        log.debug(
                "Request received to fetch cabin inventory flightInstanceId={} cabinClassId={}",
                flightInstanceId,
                cabinClassId
        );


        FlightInstanceCabinResponse response =
                flightInstanceCabinService
                        .getByFlightInstanceIdAndCabinClassId(
                                flightInstanceId,
                                cabinClassId
                        );

        log.debug(
                "Cabin inventory retrieved flightInstanceId={} cabinClassId={}",
                flightInstanceId,
                cabinClassId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns paginated cabin inventory records belonging to a flight instance.
     *
     * A single flight instance may contain multiple cabin inventories,
     * such as ECONOMY, PREMIUM_ECONOMY, BUSINESS, and FIRST.
     */
    @GetMapping("/flight-instance/{flightInstanceId}")
    public ResponseEntity<Page<FlightInstanceCabinResponse>> getByFlightInstanceId(
            @PathVariable Long flightInstanceId,
            Pageable pageable
    ) {

        log.debug(
                "Request received to fetch flight instance cabins flightInstanceId={} page={} size={}",
                flightInstanceId,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<FlightInstanceCabinResponse> response =
                flightInstanceCabinService.getByFlightInstanceId(
                        flightInstanceId,
                        pageable
                );

        log.debug(
                "Flight instance cabins retrieved flightInstanceId={} resultCount={} totalElements={}",
                flightInstanceId,
                response.getNumberOfElements(),
                response.getTotalElements()
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing FlightInstanceCabin configuration.
     *
     * The service performs the update inside a transaction and loads the
     * cabin record using the repository's locking query before modification.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightInstanceCabinResponse> updateFlightInstanceCabin(
            @PathVariable Long id,
            @Valid @RequestBody FlightInstanceCabinRequest request
    ) {

        log.info(
                "Flight instance cabin update request received cabinId={} flightInstanceId={} cabinClassId={}",
                id,
                request.getFlightInstanceId(),
                request.getCabinClassId()
        );

        FlightInstanceCabinResponse response =
                flightInstanceCabinService.updateFlightInstanceCabin(
                        id,
                        request
                );

        log.info(
                "Flight instance cabin updated successfully cabinId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a FlightInstanceCabin record.
     *
     * Deletion behavior for associated SeatInstance records depends on
     * the cascade and orphan-removal configuration of the entity relationship.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightInstanceCabin(
            @PathVariable Long id
    ) {

        log.info(
                "Flight instance cabin deletion request received cabinId={}",
                id
        );

        flightInstanceCabinService.deleteFlightInstanceCabin(
                id
        );

        log.info(
                "Flight instance cabin deleted successfully cabinId={}",
                id
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}