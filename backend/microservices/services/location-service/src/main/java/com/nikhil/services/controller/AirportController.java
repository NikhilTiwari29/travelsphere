package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.CityException;
import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.services.service.AirportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing airport reference data such as IATA codes,
 * airport details, geographical location, and city linkage.
 *
 * Gateway route: /api/airports/**
 * Airport creation and modification are restricted to authorized admin users.
 *
 * Airport data is also consumed by other microservices, such as
 * flight-ops-service and ancillary-service, for response enrichment.
 */
@RestController
@RequestMapping("/api/airports")
@RequiredArgsConstructor
@Slf4j
public class AirportController {

    private final AirportService airportService;

    // ==================== CREATE ====================

    @PostMapping
    public ResponseEntity<ApiResponse<AirportResponse>> createAirport(
            @Valid @RequestBody AirportRequest request) throws AirportException, CityException {

        log.info(
                "Received request to create airport with IATA code={}",
                request.getIataCode()
        );

        AirportResponse response = airportService.createAirport(request);

        log.info(
                "Airport created successfully with IATA code={}",
                request.getIataCode()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Airport created successfully.",
                                response
                        )
                );
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<AirportResponse>>> createBulkAirports(
            @Valid @RequestBody List<AirportRequest> requests) throws AirportException, CityException {

        log.info(
                "Received bulk airport creation request with {} records",
                requests.size()
        );

        List<AirportResponse> responses =
                airportService.createBulkAirports(requests);

        log.info(
                "Bulk airport creation completed: {} of {} records created",
                responses.size(),
                requests.size()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Airports created successfully.",
                                responses
                        )
                );
    }

    // ==================== READ ====================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AirportResponse>> getAirportById(
            @PathVariable Long id) {

        log.debug(
                "Received request to fetch airport with id={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Airport retrieved successfully.",
                        airportService.getAirportById(id)
                )
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AirportResponse>>> getAllAirports() {

        log.debug("Received request to fetch all airports");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Airports retrieved successfully.",
                        airportService.getAllAirports()
                )
        );
    }

    @GetMapping("/city/{cityId}")
    public ResponseEntity<ApiResponse<List<AirportResponse>>> getAirportsByCityId(
            @PathVariable Long cityId) {

        log.debug(
                "Received request to fetch airports for cityId={}",
                cityId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Airports retrieved successfully.",
                        airportService.getAirportsByCityId(cityId)
                )
        );
    }

    // ==================== UPDATE ====================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AirportResponse>> updateAirport(
            @PathVariable Long id,
            @Valid @RequestBody AirportRequest request) throws AirportException, CityException {

        log.info(
                "Received request to update airport id={}, IATA code={}",
                id,
                request.getIataCode()
        );

        AirportResponse response =
                airportService.updateAirport(id, request);

        log.info(
                "Airport updated successfully: id={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Airport updated successfully.",
                        response
                )
        );
    }

    // ==================== DELETE ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAirport(
            @PathVariable Long id) throws AirportException {

        log.info(
                "Received request to delete airport id={}",
                id
        );

        airportService.deleteAirport(id);

        log.info(
                "Airport deleted successfully: id={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Airport deleted successfully."
                )
        );
    }
}