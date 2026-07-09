package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.FareRulesRequest;
import com.nikhil.common_lib.payload.response.FareRulesResponse;
import com.nikhil.services.service.FareRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing change and cancellation rules associated with fares.
 *
 * Gateway route:
 *   /api/fare-rules/** → pricing-service
 *
 * Authentication:
 *   JWT authentication is required at the API Gateway.
 *
 * Fare rules define the flexibility conditions of a fare, such as:
 *   - Change eligibility
 *   - Change fees
 *   - Cancellation eligibility
 *   - Cancellation fees
 *   - Refundability conditions
 *
 * Typical Flow:
 *
 * Fare
 *   ↓
 * FareRules
 *   ↓
 * Booking UI / Booking Service
 *
 * These rules are queried when displaying fare flexibility information
 * and can later be used during change or cancellation operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/fare-rules")
@RequiredArgsConstructor
public class FareRulesController {

    private final FareRulesService fareRulesService;


    /**
     * Creates fare rules for a fare.
     *
     * The service layer is responsible for validating the referenced fare
     * and enforcing any business constraints, such as allowing only one
     * fare-rule configuration per fare.
     *
     * @param request fare-rule configuration
     * @return newly created fare rules
     */
    @PostMapping
    public ResponseEntity<FareRulesResponse> createFareRules(
            @Valid @RequestBody FareRulesRequest request) {

        log.info(
                "Received request to create fare rules fareId={}",
                request.getFareId()
        );

        FareRulesResponse response =
                fareRulesService.createFareRules(request);

        log.info(
                "Fare rules created successfully fareRulesId={} fareId={}",
                response.getId(),
                request.getFareId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Retrieves fare rules by their unique identifier.
     *
     * @param id fare-rules ID
     * @return fare-rule details
     */
    @GetMapping("/{id}")
    public ResponseEntity<FareRulesResponse> getFareRulesById(
            @PathVariable Long id) {

        log.debug(
                "Received request to fetch fare rules fareRulesId={}",
                id
        );

        FareRulesResponse response =
                fareRulesService.getFareRulesById(id);

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves the rules associated with a specific fare.
     *
     * Used by downstream services and booking/search interfaces to
     * determine the flexibility conditions of a selected fare.
     *
     * @param fareId fare ID
     * @return fare rules associated with the fare
     */
    @GetMapping("/fare/{fareId}")
    public ResponseEntity<FareRulesResponse> getFareRulesByFareId(
            @PathVariable Long fareId) {

        log.debug(
                "Received request to fetch fare rules by fareId={}",
                fareId
        );

        FareRulesResponse response =
                fareRulesService.getFareRulesByFareId(fareId);

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all fare rules belonging to fares of a specific airline.
     *
     * Useful for airline administration and fare-management interfaces.
     *
     * @param airlineId airline ID
     * @return list of fare rules belonging to the airline
     */
    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<FareRulesResponse>> getFareRulesByAirlineId(
            @PathVariable Long airlineId) {

        log.debug(
                "Received request to fetch fare rules by airlineId={}",
                airlineId
        );

        List<FareRulesResponse> responses =
                fareRulesService.getFareRulesByAirlineId(airlineId);

        log.debug(
                "Fare rules fetched successfully airlineId={} count={}",
                airlineId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates an existing fare-rule configuration.
     *
     * @param id fare-rules ID
     * @param request updated fare-rule configuration
     * @return updated fare rules
     */
    @PutMapping("/{id}")
    public ResponseEntity<FareRulesResponse> updateFareRules(
            @PathVariable Long id,
            @Valid @RequestBody FareRulesRequest request) {

        log.info(
                "Received request to update fare rules fareRulesId={} fareId={}",
                id,
                request.getFareId()
        );

        FareRulesResponse response =
                fareRulesService.updateFareRules(id, request);

        log.info(
                "Fare rules updated successfully fareRulesId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an existing fare-rule configuration.
     *
     * @param id fare-rules ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFareRules(
            @PathVariable Long id) {

        log.info(
                "Received request to delete fare rules fareRulesId={}",
                id
        );

        fareRulesService.deleteFareRules(id);

        log.info(
                "Fare rules deleted successfully fareRulesId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}