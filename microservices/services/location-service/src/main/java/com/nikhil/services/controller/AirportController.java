package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.CityException;
import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.payload.response.ApiResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.services.service.AirportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AirportController {

    private final AirportService airportService;


    // ==================== CREATE ====================

    /**
     * Creates a new airport and links it to the city specified by cityId.
     *
     * The request is validated before being passed to the service layer.
     * Returns HTTP 201 CREATED when the airport is created successfully.
     */
    @PostMapping
    public ResponseEntity<AirportResponse> createAirport(
            @Valid @RequestBody AirportRequest request)
            throws AirportException, CityException {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(airportService.createAirport(request));
    }


    /**
     * Creates multiple airports in a single request.
     *
     * The service processes each airport request, creates valid airports,
     * and skips records whose IATA code already exists or whose cityId
     * does not reference an existing city.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<AirportResponse>> createBulkAirports(
            @Valid @RequestBody List<AirportRequest> requests)
            throws AirportException, CityException {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(airportService.createBulkAirports(requests));
    }


    // ==================== READ ====================

    /**
     * Returns an airport by its database ID.
     *
     * This is the primary endpoint used by Feign clients in other
     * microservices when airport details are required for enrichment.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AirportResponse> getAirportById(
            @PathVariable Long id)
            throws AirportException {

        return ResponseEntity.ok(
                airportService.getAirportById(id)
        );
    }


    /**
     * Returns all airports available in the system.
     */
    @GetMapping
    public ResponseEntity<List<AirportResponse>> getAllAirports() {

        return ResponseEntity.ok(
                airportService.getAllAirports()
        );
    }


    /**
     * Returns all airports linked to the specified city ID.
     *
     * A city can have multiple airports, so the endpoint returns a list.
     */
    @GetMapping("/city/{cityId}")
    public ResponseEntity<List<AirportResponse>> getAirportsByCityId(
            @PathVariable Long cityId) {

        return ResponseEntity.ok(
                airportService.getAirportsByCityId(cityId)
        );
    }


    // ==================== UPDATE ====================

    /**
     * Updates an existing airport identified by its ID.
     *
     * The service validates the airport data, verifies the referenced city,
     * and prevents duplicate IATA codes from being assigned to different airports.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AirportResponse> updateAirport(
            @PathVariable Long id,
            @Valid @RequestBody AirportRequest request)
            throws AirportException, CityException {

        return ResponseEntity.ok(
                airportService.updateAirport(id, request)
        );
    }


    // ==================== DELETE ====================

    /**
     * Deletes the airport identified by the given ID.
     *
     * Returns a success message after the airport is deleted.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteAirport(
            @PathVariable Long id)
            throws AirportException {

        airportService.deleteAirport(id);

        return ResponseEntity.ok(
                new ApiResponse("Airport deleted successfully")
        );
    }
}