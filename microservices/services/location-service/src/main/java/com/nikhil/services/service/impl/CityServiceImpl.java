package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.OperationNotPermittedException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.CityRequest;
import com.nikhil.common_lib.payload.response.CityResponse;
import com.nikhil.services.mapper.CityMapper;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.CityService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles city CRUD operations, bulk creation, search, validation,
 * pagination, and Redis cache management.
 *
 * Data flow:
 * CityRequest → validation → repository → City entity → CityResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;


    // ==================== CREATE ====================

    /**
     * Creates a new city after validating the request and checking
     * that the city code does not already exist.
     */
    @Override
    public CityResponse createCity(CityRequest request)
            throws OperationNotPermittedException {

        validateCityRequest(request);

        // Prevent duplicate city codes.
        if (cityRepository.existsByCityCode(request.getCityCode())) {
            throw new OperationNotPermittedException(
                    "City with code " + request.getCityCode() + " already exists"
            );
        }

        // Convert request DTO to entity and persist it.
        City city = CityMapper.toEntity(request);
        City savedCity = cityRepository.save(city);

        log.info(
                "City created: {} ({})",
                savedCity.getName(),
                savedCity.getCityCode()
        );

        // Return API response DTO instead of exposing the entity.
        return CityMapper.toResponse(savedCity);
    }


    /**
     * Creates multiple cities in one request.
     *
     * Invalid cities and already-existing city codes are skipped,
     * while valid new cities are saved and returned.
     */
    @Override
    public List<CityResponse> createBulkCities(
            List<CityRequest> requests)
            throws OperationNotPermittedException {

        // Stores successfully created cities.
        List<CityResponse> createdCities = new ArrayList<>();

        // Stores skipped city codes for logging.
        List<String> skippedCodes = new ArrayList<>();


        // Process each city independently.
        for (CityRequest request : requests) {

            try {
                validateCityRequest(request);

            } catch (IllegalArgumentException e) {

                // Invalid request is skipped instead of failing the entire batch.
                skippedCodes.add(
                        request.getCityCode()
                                + " (invalid: "
                                + e.getMessage()
                                + ")"
                );

                continue;
            }


            // Skip city if the city code already exists in the database.
            if (cityRepository.existsByCityCode(request.getCityCode())) {

                skippedCodes.add(
                        request.getCityCode() + " (already exists)"
                );

                continue;
            }


            // Save valid and non-duplicate city.
            City city = CityMapper.toEntity(request);
            City savedCity = cityRepository.save(city);

            createdCities.add(
                    CityMapper.toResponse(savedCity)
            );
        }


        if (!skippedCodes.isEmpty()) {
            log.info(
                    "Bulk city creation - skipped: {}",
                    skippedCodes
            );
        }


        log.info(
                "Bulk city creation - created {} out of {} cities",
                createdCities.size(),
                requests.size()
        );


        return createdCities;
    }


    // ==================== READ ====================

    /**
     * Returns a city by ID.
     *
     * The result is cached in Redis using the city ID as the cache key.
     * On a cache hit, the database query is skipped.
     */
    @Override
    @Cacheable(
            cacheNames = "cities",
            key = "#id"
    )
    public CityResponse getCityById(Long id)
            throws ResourceNotFoundException {

        log.info("CACHE MISS - Fetching city {} from database", id);

        // Query database when the city is not available in cache.
        City city = cityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "City not found with id: " + id
                        )
                );

        return CityMapper.toResponse(city);
    }


    /**
     * Returns all cities using pagination and sorting information
     * provided through Pageable.
     */
    @Override
    public Page<CityResponse> getAllCities(Pageable pageable) {

        return cityRepository
                .findAll(pageable)
                .map(CityMapper::toResponse);
    }


    // ==================== UPDATE ====================

    /**
     * Updates an existing city.
     *
     * Validates the request and ensures that the requested city code
     * is not already used by another city.
     *
     * Cached city data is evicted after a successful update to prevent
     * stale data from being returned.
     */
    @Override
    @Caching(evict = {

            // Remove cached city for the updated ID.
            @CacheEvict(
                    cacheNames = "cities",
                    key = "#id"
            ),

            // Clear code-based city cache because cityCode may have changed.
            @CacheEvict(
                    cacheNames = "citiesByCode",
                    allEntries = true
            )
    })
    public CityResponse updateCity(
            Long id,
            CityRequest request)
            throws ResourceNotFoundException,
            OperationNotPermittedException {

        // Find the city that needs to be updated.
        City city = cityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "City not found with id: " + id
                        )
                );


        // Validate city code, country code, and time-zone offset.
        validateCityRequest(request, id);


        /*
         * Check whether the city code sent in the update request is already used
         * by another record, excluding the record currently being updated.
         *
         * Example: Suppose Mumbai (ID 3) currently has code BOM.
         *
         * - If the request still contains BOM, allow the update because BOM belongs
         *   to the same record (ID 3). This is common in PUT requests where unchanged
         *   fields are also sent again.
         *
         * - If the request changes BOM to BLR, check whether BLR is used by any record
         *   other than ID 3. If Bengaluru (ID 4) already has BLR, reject the update
         *   to prevent two cities from having the same city code.
         */
        if (cityRepository.existsByCityCodeAndIdNot(
                request.getCityCode(),
                id)) {

            throw new OperationNotPermittedException(
                    "City with code "
                            + request.getCityCode()
                            + " already exists"
            );
        }


        // Apply request values to the existing entity and save changes.
        City updatedCity = cityRepository.save(
                CityMapper.updateEntity(city, request)
        );


        log.info(
                "City updated: {} ({})",
                updatedCity.getName(),
                updatedCity.getCityCode()
        );


        return CityMapper.toResponse(updatedCity);
    }


    // ==================== DELETE ====================

    /**
     * Deletes a city by ID.
     *
     * Related cache entries are removed after successful deletion
     * so deleted city data cannot be returned from Redis.
     */
    @Override
    @Caching(evict = {

            // Remove the city cached by ID.
            @CacheEvict(
                    cacheNames = "cities",
                    key = "#id"
            ),

            // Clear any code-based cached city data.
            @CacheEvict(
                    cacheNames = "citiesByCode",
                    allEntries = true
            )
    })
    public void deleteCity(Long id)
            throws ResourceNotFoundException {

        // Verify that the city exists before deleting it.
        City city = cityRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "City not found with id: " + id
                        )
                );


        cityRepository.delete(city);


        log.info(
                "City deleted: {} ({})",
                city.getName(),
                city.getCityCode()
        );
    }


    // ==================== SEARCH & QUERY ====================

    /**
     * Searches cities using a keyword across searchable city fields
     * and returns paginated results.
     */
    @Override
    public Page<CityResponse> searchCities(
            String keyword,
            Pageable pageable) {

        return cityRepository
                .searchByKeyword(keyword, pageable)
                .map(CityMapper::toResponse);
    }


    /**
     * Returns paginated cities belonging to the specified country code.
     */
    @Override
    public Page<CityResponse> getCitiesByCountryCode(
            String countryCode,
            Pageable pageable) {

        return cityRepository
                .findByCountryCodeIgnoreCase(
                        countryCode,
                        pageable
                )
                .map(CityMapper::toResponse);
    }


    // ==================== VALIDATION ====================

    /**
     * Checks whether a city with the given city code exists.
     */
    @Override
    public boolean cityExists(String cityCode) {

        return cityRepository.existsByCityCode(cityCode);
    }


    /**
     * Validates the city-code format.
     *
     * Valid format:
     * - 2 to 10 characters
     * - uppercase letters and numbers only
     *
     * Examples:
     * DEL
     * BLR
     * CITY01
     */
    @Override
    public boolean validateCityCode(String cityCode) {

        return cityCode != null
                && cityCode.length() <= 10
                && cityCode.matches("[A-Z0-9]{2,10}");
    }


    // ==================== PRIVATE HELPERS ====================

    /**
     * Validates the common fields of a city request.
     */
    private void validateCityRequest(CityRequest request) {

        validateCityRequest(request, null);
    }


    /**
     * Performs business-level validation for city code,
     * country code, and optional time-zone offset.
     *
     * Note: excludeId is currently not used inside this method.
     * Duplicate-code validation for updates is handled separately
     * using existsByCityCodeAndIdNot().
     */
    private void validateCityRequest(
            CityRequest request,
            Long excludeId) {


        // City code must contain 2-10 uppercase letters or numbers.
        if (!validateCityCode(request.getCityCode())) {

            throw new IllegalArgumentException(
                    "Invalid city code format. "
                            + "Must be 2-10 alphanumeric characters."
            );
        }


        // Country code must contain 2-5 uppercase letters.
        if (request.getCountryCode() == null
                || !request.getCountryCode()
                .matches("[A-Z]{2,5}")) {

            throw new IllegalArgumentException(
                    "Country code must be 2-5 uppercase letters"
            );
        }


        // Optional time-zone offset must follow formats such as +05:30 or -04:00.
        if (request.getTimeZoneOffset() != null
                && !request.getTimeZoneOffset()
                .matches("[+-]\\d{2}:\\d{2}")) {

            throw new IllegalArgumentException(
                    "Time zone offset must be in format ±HH:MM"
            );
        }
    }
}