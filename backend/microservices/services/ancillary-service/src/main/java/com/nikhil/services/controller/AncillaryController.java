package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AncillaryRequest;
import com.nikhil.common_lib.payload.response.AncillaryResponse;
import com.nikhil.services.service.AncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing airline-level ancillary product definitions.
 *
 * Gateway route:
 *   /api/ancillaries/** → pricing-service
 *
 * Authentication:
 *   JWT authentication is required through the API Gateway.
 *
 * Ancillaries represent optional products or services that customers
 * can purchase in addition to the base flight fare.
 *
 * Examples:
 *   - Travel insurance
 *   - Lounge access
 *   - Priority boarding
 *   - Priority check-in
 *   - Airport transfer
 *   - Extra baggage
 *   - In-flight meals
 *   - Wi-Fi packages
 *
 * Ownership resolution:
 *
 * X-User-Id
 *      ↓
 * AncillaryService
 *      ↓
 * AirlineIntegrationService
 *      ↓
 * Airline Core Service (Feign)
 *      ↓
 * Resolve Airline ID
 *
 * Typical booking flow:
 *
 * Flight Search
 *      ↓
 * Fare Selection
 *      ↓
 * AncillaryClient
 *      ↓
 * GET /api/ancillaries
 *      ↓
 * Available airline ancillary products
 *      ↓
 * Customer selects optional services
 *
 * booking-service can read available ancillary offerings through its
 * AncillaryClient integration.
 *
 * This controller handles only HTTP request/response concerns.
 * Business validation, airline resolution, ownership checks, and persistence
 * operations are delegated to AncillaryService.
 */
@Slf4j
@RestController
@RequestMapping("/api/ancillaries")
@RequiredArgsConstructor
public class AncillaryController {

    private final AncillaryService ancillaryService;


    /**
     * Creates a new ancillary product for the authenticated airline owner.
     *
     * The authenticated user's ID is received from the trusted gateway
     * header and passed to the service layer.
     *
     * The service layer is responsible for resolving the Airline ID
     * associated with the user and creating the ancillary product under
     * that airline.
     *
     * Processing flow:
     *
     * POST /api/ancillaries
     *          ↓
     * X-User-Id
     *          ↓
     * Resolve Airline
     *          ↓
     * Validate ancillary request
     *          ↓
     * Create Ancillary
     *          ↓
     * Return AncillaryResponse
     *
     * @param request ancillary-product creation request
     * @param userId authenticated user ID propagated by the API Gateway
     * @return created ancillary product
     * @throws ResourceNotFoundException if the airline associated with the
     *                                   user cannot be resolved
     */
    @PostMapping
    public ResponseEntity<AncillaryResponse> create(
            @Valid @RequestBody AncillaryRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to create ancillary userId={}",
                userId
        );

        AncillaryResponse response =
                ancillaryService.create(userId, request);

        log.info(
                "Ancillary created successfully ancillaryId={} userId={}",
                response.getId(),
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves an ancillary product by its unique identifier.
     *
     * This endpoint can be used by internal services or administration
     * interfaces when details of a specific ancillary product are required.
     *
     * @param id ancillary ID
     * @return ancillary-product details
     * @throws ResourceNotFoundException if the ancillary does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<AncillaryResponse> getById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch ancillary ancillaryId={}",
                id
        );

        AncillaryResponse response =
                ancillaryService.getById(id);

        log.debug(
                "Ancillary fetched successfully ancillaryId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all ancillary products configured for the authenticated
     * airline owner.
     *
     * The service layer resolves the Airline ID from X-User-Id and returns
     * only the ancillary products belonging to that airline.
     *
     * Processing flow:
     *
     * X-User-Id
     *      ↓
     * Resolve Airline ID
     *      ↓
     * Fetch airline ancillary products
     *      ↓
     * Return List<AncillaryResponse>
     *
     * @param userId authenticated user ID propagated by the API Gateway
     * @return ancillary products belonging to the user's airline
     */
    @GetMapping
    public ResponseEntity<List<AncillaryResponse>> getAllByAirlineId(
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Received request to fetch ancillaries for authenticated user userId={}",
                userId
        );

        List<AncillaryResponse> responses =
                ancillaryService.getAllByAirlineId(userId);

        log.debug(
                "Ancillaries fetched successfully userId={} count={}",
                userId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates an existing ancillary product.
     *
     * The service layer is responsible for:
     *   - Validating that the ancillary exists
     *   - Applying the requested changes
     *   - Persisting the updated entity
     *
     * @param id ancillary ID
     * @param request updated ancillary-product values
     * @return updated ancillary product
     * @throws ResourceNotFoundException if the ancillary does not exist
     */
    @PutMapping("/{id}")
    public ResponseEntity<AncillaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update ancillary ancillaryId={}",
                id
        );

        AncillaryResponse response =
                ancillaryService.update(id, request);

        log.info(
                "Ancillary updated successfully ancillaryId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an ancillary product.
     *
     * After successful deletion, the endpoint returns HTTP 204 No Content.
     *
     * @param id ancillary ID
     * @return HTTP 204 No Content after successful deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to delete ancillary ancillaryId={}",
                id
        );

        ancillaryService.delete(id);

        log.info(
                "Ancillary deleted successfully ancillaryId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}