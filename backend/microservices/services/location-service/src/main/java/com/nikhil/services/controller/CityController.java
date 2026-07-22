package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.OperationNotPermittedException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.CityRequest;
import com.nikhil.common_lib.payload.response.CityResponse;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * REST API for managing city reference data.
 *
 * Provides city CRUD, bulk creation, search, country-based filtering,
 * pagination, sorting, and city-code existence checks.
 */
@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
@Slf4j
public class CityController {

    private final CityService cityService;


    // ==================== CREATE ====================

    /**
     * Creates a new city after request validation
     * and duplicate city-code checks.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CityResponse>> createCity(
            @Valid @RequestBody CityRequest request) {

        log.info(
                "Received request to create city with code={}",
                request.getCityCode()
        );

        CityResponse response =
                cityService.createCity(request);

        CityResponse response = cityService.createCity(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "City created successfully.",
                                response
                        )
                );
    }


    /**
     * Creates multiple cities in one request.
     *
     * Invalid requests and existing city codes are skipped
     * by the service layer.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<CityResponse>>> createBulkCities(
            @Valid @RequestBody List<CityRequest> requests)
            throws OperationNotPermittedException {

        log.info(
                "Received bulk city creation request with {} records",
                requests.size()
        );

        List<CityResponse> responses =
                cityService.createBulkCities(requests);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Cities created successfully.",
                                responses
                        )
                );
    }


    // ==================== READ ====================

    /**
     * Returns a city by its database ID.
     *
     * The service may return the response from Redis cache
     * or fetch it from the database on a cache miss.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CityResponse>> getCityById(
            @PathVariable Long id)
            throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch city with id={}",
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "City retrieved successfully.",
                        cityService.getCityById(id)
                )
        );
    }


    /**
     * Returns cities with pagination and dynamic sorting.
     *
     * Example:
     * GET /api/cities?page=0&size=20&sortBy=name&sortDirection=asc
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CityResponse>>> getAllCities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        log.debug(
                "Fetching cities: page={}, size={}, sortBy={}, direction={}",
                page,
                size,
                sortBy,
                sortDirection
        );

        // Build sorting configuration from request parameters.
        Sort sort = Sort.by(
                Sort.Direction.fromString(sortDirection),
                sortBy
        );

        // Create pagination configuration with sorting.
        Pageable pageable = PageRequest.of(
                page,
                size,
                sort
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cities retrieved successfully.",
                        cityService.getAllCities(pageable)
                )
        );
    }


    // ==================== UPDATE ====================

    /**
     * Updates an existing city.
     *
     * The service ensures that the requested city code is not
     * already assigned to another city.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CityResponse>> updateCity(
            @PathVariable Long id,
            @Valid @RequestBody CityRequest request)
            throws ResourceNotFoundException,
            OperationNotPermittedException {

        log.info(
                "Received request to update city id={}, code={}",
                id,
                request.getCityCode()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "City updated successfully.",
                        cityService.updateCity(id, request)
                )
        );
    }


    // ==================== DELETE ====================

    /**
     * Deletes a city by its database ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCity(
            @PathVariable Long id)
            throws ResourceNotFoundException {

        log.info(
                "Received request to delete city id={}",
                id
        );

        cityService.deleteCity(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "City deleted successfully."
                )
        );
    }


    // ==================== SEARCH & QUERY ====================

    /**
     * Searches cities by keyword with pagination.
     *
     * The keyword can match city name, city code, country code,
     * country name, or region code.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CityResponse>>> searchCities(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug(
                "Searching cities: keyword={}, page={}, size={}",
                keyword,
                page,
                size
        );

        Pageable pageable =
                PageRequest.of(page, size);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cities retrieved successfully.",
                        cityService.searchCities(
                                keyword,
                                pageable
                        )
                )
        );
    }


    /**
     * Returns cities belonging to the given country code.
     *
     * Example:
     * GET /api/cities/country/IN
     */
    @GetMapping("/country/{countryCode}")
    public ResponseEntity<ApiResponse<Page<CityResponse>>> getCitiesByCountryCode(
            @PathVariable String countryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug(
                "Fetching cities by countryCode={}, page={}, size={}",
                countryCode,
                page,
                size
        );

        Pageable pageable =
                PageRequest.of(page, size);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cities retrieved successfully.",
                        cityService.getCitiesByCountryCode(
                                countryCode.toUpperCase(),
                                pageable
                        )
                )
        );
    }


    // ==================== VALIDATION ====================

    /**
     * Checks whether a city with the given city code already exists.
     *
     * Example:
     * GET /api/cities/exists/BOM
     */
    @GetMapping("/exists/{cityCode}")
    public ResponseEntity<ApiResponse<Boolean>> checkCityExists(
            @PathVariable String cityCode) {

        log.debug(
                "Checking city existence for code={}",
                cityCode
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "City existence checked successfully.",
                        cityService.cityExists(
                                cityCode.toUpperCase()
                        )
                )
        );
    }
}