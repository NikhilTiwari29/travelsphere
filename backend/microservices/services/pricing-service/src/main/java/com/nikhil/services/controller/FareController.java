package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.model.Fare;
import com.nikhil.services.service.FareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * REST API for managing and querying flight fares.
 *
 * Gateway route: /api/fares/** → pricing-service.
 * Provides CRUD operations and batch fare lookups used by
 * Flight Ops and Booking services.
 */
@Slf4j
@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
public class FareController {

    private final FareService fareService;


    // ==================== Create Operations ====================

    /**
     * Creates a fare configuration for a flight and cabin class.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FareResponse>> createFare(
            @Valid @RequestBody FareRequest request
    )  {

        log.info(
                "Fare creation request received flightId={} cabinClassId={}",
                request.getFlightId(),
                request.getCabinClassId()
        );

        FareResponse response =
                fareService.createFare(request);

        log.info(
                "Fare created successfully fareId={} flightId={} cabinClassId={}",
                response.getId(),
                request.getFlightId(),
                request.getCabinClassId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Fare created successfully.",
                                response
                        )
                );
    }


    /**
     * Creates multiple fare configurations in a single request.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<FareResponse>>> createFares(
            @Valid @RequestBody List<FareRequest> requests
    )  {

        log.info(
                "Bulk fare creation request received requestedCount={}",
                requests.size()
        );

        List<FareResponse> responses =
                fareService.createFares(requests);

        log.info(
                "Bulk fare creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Fares created successfully.",
                                responses
                        )
                );
    }


// ==================== Read Operations ====================

    /**
     * Returns all configured fares.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Fare>>> getFares()
             {

        log.debug(
                "Request received to fetch all fares"
        );

        List<Fare> fares =
                fareService.getFares();

        log.debug(
                "Fare retrieval completed returnedCount={}",
                fares.size()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fares retrieved successfully.",
                        fares
                )
        );
    }


    /**
     * Returns a fare by its database identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FareResponse>> getFareById(
            @PathVariable Long id
    )  {

        log.debug(
                "Fare lookup request received fareId={}",
                id
        );

        FareResponse response =
                fareService.getFareById(id);

        log.debug(
                "Fare lookup completed fareId={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fare retrieved successfully.",
                        response
                )
        );
    }


    /**
     * Returns all fares configured for a specific flight
     * and cabin class combination.
     */
    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<ApiResponse<List<FareResponse>>>
    getFaresByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId
    )  {

        log.debug(
                "Fare lookup request received flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        List<FareResponse> responses =
                fareService.getFaresByFlightIdAndCabinClassId(
                        flightId,
                        cabinClassId
                );

        log.debug(
                "Fare lookup completed flightId={} cabinClassId={} returnedCount={}",
                flightId,
                cabinClassId,
                responses.size()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fares retrieved successfully.",
                        responses
                )
        );
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing fare configuration.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FareResponse>> updateFare(
            @PathVariable Long id,
            @Valid @RequestBody FareRequest request
    )  {

        log.info(
                "Fare update request received fareId={}",
                id
        );

        FareResponse response =
                fareService.updateFare(
                        id,
                        request
                );

        log.info(
                "Fare updated successfully fareId={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fare updated successfully.",
                        response
                )
        );
    }


// ==================== Delete Operations ====================

    /**
     * Deletes a fare configuration by its identifier.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFare(
            @PathVariable Long id
    )  {

        log.info(
                "Fare deletion request received fareId={}",
                id
        );

        fareService.deleteFare(id);

        log.info(
                "Fare deleted successfully fareId={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fare deleted successfully."
                )
        );
    }


// ==================== Batch Operations ====================

    /**
     * Returns multiple fares using their identifiers in a single request.
     *
     * Internal services use this endpoint to avoid making one network
     * request for each required fare.
     */
    @PostMapping("/batch-by-ids")
    public ResponseEntity<ApiResponse<Map<Long, FareResponse>>> getFaresByIds(
            @RequestBody List<Long> ids
    )  {

        log.debug(
                "Batch fare lookup request received requestedCount={}",
                ids.size()
        );

        Map<Long, FareResponse> responses =
                fareService.getFaresByIds(ids);

        log.debug(
                "Batch fare lookup completed requestedCount={} returnedCount={}",
                ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Fares retrieved successfully.",
                        responses
                )
        );
    }


    /**
     * Returns the lowest fare for each requested flight within
     * the specified cabin class.
     *
     * Flight Ops uses this batch endpoint during flight search
     * to enrich multiple results with pricing information.
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Map<Long, FareResponse>>>
    getLowestFarePerFlight(
            @RequestBody List<Long> flightIds,
            @RequestParam Long cabinClassId
    )  {

        log.debug(
                "Lowest fare batch search request received flightCount={} cabinClassId={}",
                flightIds.size(),
                cabinClassId
        );

        Map<Long, FareResponse> responses =
                fareService.getLowestFarePerFlight(
                        flightIds,
                        cabinClassId
                );

        log.debug(
                "Lowest fare batch search completed requestedFlightCount={} matchedFlightCount={} cabinClassId={}",
                flightIds.size(),
                responses.size(),
                cabinClassId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lowest fares retrieved successfully.",
                        responses
                )
        );
    }


    /**
     * Returns the lowest fare available for a specific
     * flight and cabin class combination.
     */
    @GetMapping("/lowest/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<ApiResponse<FareResponse>>
    getLowestFareForFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId
    ) {

        log.debug(
                "Lowest fare lookup request received flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        FareResponse response =
                fareService.getLowestFareForFlightAndCabin(
                        flightId,
                        cabinClassId
                );

        log.debug(
                "Lowest fare lookup completed flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lowest fare retrieved successfully.",
                        response
                )
        );
    }
}