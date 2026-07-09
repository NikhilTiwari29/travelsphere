package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.InsuranceCoverageRequest;
import com.nikhil.common_lib.payload.response.ApiResponse;
import com.nikhil.common_lib.payload.response.InsuranceCoverageResponse;
import com.nikhil.services.service.InsuranceCoverageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * REST API for managing insurance coverage options linked to ancillary products.
 *
 * Gateway route: /api/insurance-coverages/**.
 * Coverage records define insurance tiers and benefits that can be attached
 * to travel-protection ancillary products.
 */
@Slf4j
@RestController
@RequestMapping("/api/insurance-coverages")
@RequiredArgsConstructor
public class InsuranceCoverageController {

    private final InsuranceCoverageService coverageService;


    // ==================== Create Operations ====================

    /**
     * Creates an insurance coverage configuration for an ancillary product.
     */
    @PostMapping
    public ResponseEntity<InsuranceCoverageResponse> createCoverage(
            @Valid @RequestBody InsuranceCoverageRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Insurance coverage creation request received userId={} ancillaryId={}",
                userId,
                request.getAncillaryId()
        );

        InsuranceCoverageResponse response =
                coverageService.createCoverage(request);

        log.info(
                "Insurance coverage created successfully coverageId={} ancillaryId={} userId={}",
                response.getId(),
                request.getAncillaryId(),
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple insurance coverage configurations in a single request.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<InsuranceCoverageResponse>> createCoveragesBulk(
            @Valid @RequestBody List<InsuranceCoverageRequest> requests,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Bulk insurance coverage creation request received userId={} requestedCount={}",
                userId,
                requests.size()
        );

        List<InsuranceCoverageResponse> responses =
                coverageService.createCoveragesBulk(requests);

        log.info(
                "Bulk insurance coverage creation completed userId={} requestedCount={} createdCount={}",
                userId,
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    // ==================== Read Operations ====================

    /**
     * Returns an insurance coverage configuration by its identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InsuranceCoverageResponse> getCoverageById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Insurance coverage lookup request received coverageId={}",
                id
        );

        InsuranceCoverageResponse response =
                coverageService.getCoverageById(id);

        log.debug(
                "Insurance coverage lookup completed coverageId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Returns all configured insurance coverage records.
     */
    @GetMapping
    public ResponseEntity<List<InsuranceCoverageResponse>> getAllCoverages() {

        log.debug(
                "Request received to fetch all insurance coverages"
        );

        List<InsuranceCoverageResponse> responses =
                coverageService.getAllCoverages();

        log.debug(
                "Insurance coverage retrieval completed returnedCount={}",
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns all insurance coverage options configured for an ancillary product.
     */
    @GetMapping("/ancillary/{ancillaryId}")
    public ResponseEntity<List<InsuranceCoverageResponse>>
    getCoveragesByAncillaryId(
            @PathVariable Long ancillaryId
    ) {

        log.debug(
                "Ancillary coverage lookup request received ancillaryId={}",
                ancillaryId
        );

        List<InsuranceCoverageResponse> responses =
                coverageService.getCoveragesByAncillaryId(
                        ancillaryId
                );

        log.debug(
                "Ancillary coverage lookup completed ancillaryId={} returnedCount={}",
                ancillaryId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Returns only active insurance coverage options available for
     * the specified ancillary product.
     */
    @GetMapping("/ancillary/{ancillaryId}/active")
    public ResponseEntity<List<InsuranceCoverageResponse>>
    getActiveCoveragesByAncillaryId(
            @PathVariable Long ancillaryId
    ) {

        log.debug(
                "Active ancillary coverage lookup request received ancillaryId={}",
                ancillaryId
        );

        List<InsuranceCoverageResponse> responses =
                coverageService.getActiveCoveragesByAncillaryId(
                        ancillaryId
                );

        log.debug(
                "Active ancillary coverage lookup completed ancillaryId={} returnedCount={}",
                ancillaryId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing insurance coverage configuration.
     */
    @PutMapping("/{id}")
    public ResponseEntity<InsuranceCoverageResponse> updateCoverage(
            @PathVariable Long id,
            @Valid @RequestBody InsuranceCoverageRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Insurance coverage update request received coverageId={} ancillaryId={}",
                id,
                request.getAncillaryId()
        );

        InsuranceCoverageResponse response =
                coverageService.updateCoverage(
                        id,
                        request
                );

        log.info(
                "Insurance coverage updated successfully coverageId={} ancillaryId={}",
                id,
                request.getAncillaryId()
        );

        return ResponseEntity.ok(response);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes an insurance coverage configuration by its identifier.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCoverage(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Insurance coverage deletion request received coverageId={} userId={}",
                id,
                userId
        );

        coverageService.deleteCoverage(id);

        log.info(
                "Insurance coverage deleted successfully coverageId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(
                new ApiResponse(
                        "Coverage deleted successfully"
                )
        );
    }
}