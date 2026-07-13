package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.BaggagePolicyRequest;
import com.nikhil.common_lib.payload.response.BaggagePolicyResponse;
import com.nikhil.services.service.BaggagePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing baggage allowance policies associated with fares.
 *
 * Gateway route:
 *   /api/baggage-policies/** → pricing-service
 *
 * Authentication:
 *   JWT authentication is required through the API Gateway.
 *
 * BaggagePolicy defines baggage allowances and restrictions associated
 * with a particular Fare, such as:
 *   - Cabin baggage allowance
 *   - Cabin baggage weight limit
 *   - Checked baggage allowance
 *   - Checked baggage weight limit
 *   - Excess baggage charges
 *
 * Typical relationship:
 *
 * Fare
 *   ↓
 * BaggagePolicy
 *
 * Typical booking flow:
 *
 * Flight Search
 *      ↓
 * Fare Selection
 *      ↓
 * Fare Details
 *      ↓
 * BaggagePolicy Lookup
 *      ↓
 * Display baggage allowance to customer
 *
 * This controller only handles HTTP request/response concerns.
 * Business validation and persistence operations are delegated to
 * BaggagePolicyService.
 *
 * This controller has no direct Feign Client dependencies.
 */
@Slf4j
@RestController
@RequestMapping("/api/baggage-policies")
@RequiredArgsConstructor
public class BaggagePolicyController {

    private final BaggagePolicyService baggagePolicyService;


    /**
     * Creates a baggage policy for a fare.
     *
     * The service layer is responsible for:
     *   - Validating the referenced Fare
     *   - Validating baggage-policy business rules
     *   - Preventing duplicate policies when required
     *   - Persisting the policy
     *
     * @param request baggage-policy creation request
     * @return newly created baggage policy
     */
    @PostMapping
    public ResponseEntity<BaggagePolicyResponse> createBaggagePolicy(
            @Valid @RequestBody BaggagePolicyRequest request
    ) {

        log.info(
                "Received request to create baggage policy fareId={}",
                request.getFareId()
        );

        BaggagePolicyResponse response =
                baggagePolicyService.createBaggagePolicy(request);

        log.info(
                "Baggage policy created successfully baggagePolicyId={} fareId={}",
                response.getId(),
                request.getFareId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple baggage policies in a single request.
     *
     * This endpoint is useful during airline fare configuration where
     * baggage policies for multiple fares are created together.
     *
     * Request flow:
     *
     * List<BaggagePolicyRequest>
     *          ↓
     * Validate requests
     *          ↓
     * Service bulk creation
     *          ↓
     * Persist policies
     *          ↓
     * Return created policies
     *
     * @param requests list of baggage-policy creation requests
     * @return list of newly created baggage policies
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<BaggagePolicyResponse>> createBaggagePolicies(
            @Valid @RequestBody List<BaggagePolicyRequest> requests
    ) {

        log.info(
                "Received request to create baggage policies in bulk count={}",
                requests.size()
        );

        List<BaggagePolicyResponse> responses =
                baggagePolicyService.createBaggagePolicies(requests);

        log.info(
                "Bulk baggage policy creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    /**
     * Retrieves a baggage policy by its unique identifier.
     *
     * @param id baggage-policy ID
     * @return baggage-policy details
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyById(
            @PathVariable Long id
    ) {

        log.debug(
                "Received request to fetch baggage policy baggagePolicyId={}",
                id
        );

        BaggagePolicyResponse response =
                baggagePolicyService.getBaggagePolicyById(id);

        log.debug(
                "Baggage policy fetched successfully baggagePolicyId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves the baggage policy associated with a specific fare.
     *
     * This endpoint can be used during fare selection and booking flows
     * to display baggage allowances for the selected fare product.
     *
     * @param fareId fare ID
     * @return baggage policy associated with the fare
     */
    @GetMapping("/fare/{fareId}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyByFareId(
            @PathVariable Long fareId
    ) {

        log.debug(
                "Received request to fetch baggage policy by fareId={}",
                fareId
        );

        BaggagePolicyResponse response =
                baggagePolicyService.getBaggagePolicyByFareId(fareId);

        log.debug(
                "Baggage policy fetched successfully fareId={}",
                fareId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all baggage policies associated with an airline.
     *
     * This endpoint is primarily useful for airline administration
     * and fare-management interfaces.
     *
     * @param airlineId airline ID
     * @return list of baggage policies belonging to the airline
     */
    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<BaggagePolicyResponse>> getBaggagePoliciesByAirlineId(
            @PathVariable Long airlineId
    ) {

        log.debug(
                "Received request to fetch baggage policies airlineId={}",
                airlineId
        );

        List<BaggagePolicyResponse> responses =
                baggagePolicyService.getBaggagePoliciesByAirlineId(airlineId);

        log.debug(
                "Baggage policies fetched successfully airlineId={} count={}",
                airlineId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates an existing baggage policy.
     *
     * The service layer validates that the policy exists before applying
     * and persisting the requested changes.
     *
     * @param id baggage-policy ID
     * @param request updated baggage-policy configuration
     * @return updated baggage policy
     */
    @PutMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> updateBaggagePolicy(
            @PathVariable Long id,
            @Valid @RequestBody BaggagePolicyRequest request
    ) {

        log.info(
                "Received request to update baggage policy baggagePolicyId={} fareId={}",
                id,
                request.getFareId()
        );

        BaggagePolicyResponse response =
                baggagePolicyService.updateBaggagePolicy(id, request);

        log.info(
                "Baggage policy updated successfully baggagePolicyId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an existing baggage policy.
     *
     * The service layer verifies that the requested policy exists
     * before performing the deletion.
     *
     * @param id baggage-policy ID
     * @return HTTP 204 No Content after successful deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBaggagePolicy(
            @PathVariable Long id
    ) {

        log.info(
                "Received request to delete baggage policy baggagePolicyId={}",
                id
        );

        baggagePolicyService.deleteBaggagePolicy(id);

        log.info(
                "Baggage policy deleted successfully baggagePolicyId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}