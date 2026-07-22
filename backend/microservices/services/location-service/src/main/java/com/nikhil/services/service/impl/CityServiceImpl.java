package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.BadRequestException;
import com.nikhil.common_lib.exception.OperationNotPermittedException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.CityRequest;
import com.nikhil.common_lib.payload.response.CityResponse;
import com.nikhil.services.exception.CityAlreadyExistsException;
import com.nikhil.services.exception.CityNotFoundException;
import com.nikhil.services.mapper.CityMapper;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles city CRUD operations, bulk creation, search, validation,
 * pagination, and Redis cache management.
 *
 * Data flow:
 * CityRequest → validation → City entity → repository → CityResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;


    // ==================== CREATE ====================

    /**
     * Creates a city after validating the request and checking
     * that the city code does not already exist.
     */
    @Override

    /*
     * Keeps validation and database save inside one transaction.
     * An unhandled runtime exception rolls back database changes.
     */
    @Transactional
    public CityResponse createCity(CityRequest request)
            throws OperationNotPermittedException {

        validateCityRequest(request);


        // Prevent duplicate city codes.
        if (cityRepository.existsByCityCode(
                request.getCityCode())) {

            throw new CityAlreadyExistsException(
                    request.getCityCode()
            );
        }


        // Convert request DTO to entity.
        City city = CityMapper.toEntity(request);


        // Persist the city.
        City savedCity = cityRepository.save(city);


        log.info(
                "City created: id={}, name={}, code={}",
                savedCity.getId(),
                savedCity.getName(),
                savedCity.getCityCode()
        );


        // Return response DTO instead of exposing the entity.
        return CityMapper.toResponse(savedCity);
    }


    /**
     * Creates multiple cities in one request.
     *
     * Invalid cities and existing city codes are skipped.
     * Valid cities are saved and returned.
     */
    @Override

    /*
     * Runs the bulk database operation in one transaction.
     *
     * Records handled using continue are intentionally skipped.
     * An unhandled runtime exception can roll back the transaction.
     */
    @Transactional
    public List<CityResponse> createBulkCities(
            List<CityRequest> requests)
            throws OperationNotPermittedException {

        // Stores successfully created cities.
        List<CityResponse> createdCities =
                new ArrayList<>();


        // Stores skipped city codes and reasons.
        List<String> skippedCodes =
                new ArrayList<>();


        for (CityRequest request : requests) {

            try {

                validateCityRequest(request);

            } catch (IllegalArgumentException e) {

                // Skip invalid city request.
                skippedCodes.add(
                        request.getCityCode()
                                + " (invalid: "
                                + e.getMessage()
                                + ")"
                );

                continue;
            }


            // Skip if the city code already exists.
            if (cityRepository.existsByCityCode(
                    request.getCityCode())) {

                skippedCodes.add(
                        request.getCityCode()
                                + " (already exists)"
                );

                continue;
            }


            // Convert and save valid city.
            City city =
                    CityMapper.toEntity(request);

            City savedCity =
                    cityRepository.save(city);


            createdCities.add(
                    CityMapper.toResponse(savedCity)
            );
        }


        if (!skippedCodes.isEmpty()) {

            log.info(
                    "Bulk city creation skipped: {}",
                    skippedCodes
            );
        }


        log.info(
                "Bulk city creation completed: created {} of {} cities",
                createdCities.size(),
                requests.size()
        );


        return createdCities;
    }


    // ==================== READ ====================

    /**
     * Returns a city by ID.
     *
     * The response is cached in Redis using the city ID.
     * On a cache hit, the database query is skipped.
     *
     * No service-level transaction is needed because this is a simple
     * repository lookup and CityResponse mapping does not require
     * lazy relationship loading.
     */
    @Override
    @Cacheable(
            cacheNames = "cities",
            key = "#id"
    )
    public CityResponse getCityById(Long id)
            throws ResourceNotFoundException {

        /*
         * Runs only on a cache miss because @Cacheable skips
         * method execution when cities::{id} exists in Redis.
         */
        log.debug(
                "Cache miss for city id={}; fetching from database",
                id
        );


        City city = cityRepository
                .findById(id)
                .orElseThrow(() ->
                        new CityNotFoundException(id)
                );


        return CityMapper.toResponse(city);
    }


    /**
     * Returns cities with pagination and sorting.
     *
     * No service-level transaction is needed because the repository
     * query returns all data required for DTO mapping.
     */
    @Override
    public Page<CityResponse> getAllCities(
            Pageable pageable) {

        log.debug(
                "Fetching cities: page={}, size={}, sort={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );


        return cityRepository
                .findAll(pageable)
                .map(CityMapper::toResponse);
    }


    // ==================== UPDATE ====================

    /**
     * Updates an existing city.
     *
     * The request is validated and the requested city code is checked
     * to ensure it is not already used by another city.
     */
    @Override

    /*
     * Keeps city lookup, validation, entity modification, and database
     * update inside one transaction.
     */
    @Transactional
    @Caching(

            /*
             * Replace cities::{id} with the updated CityResponse
             * returned by this method.
             */
            put = {
                    @CachePut(
                            cacheNames = "cities",
                            key = "#id"
                    )
            },

            /*
             * Clear code-based entries because cityCode may have changed.
             */
            evict = {
                    @CacheEvict(
                            cacheNames = "citiesByCode",
                            allEntries = true
                    )
            }
    )
    public CityResponse updateCity(
            Long id,
            CityRequest request)
            throws ResourceNotFoundException,
            OperationNotPermittedException {


        // Find the city being updated.
        City city = cityRepository
                .findById(id)
                .orElseThrow(() ->
                        new CityNotFoundException(id)
                );


        // Validate city code, country code, and timezone data.
        validateCityRequest(request, id);


        /*
         * Check whether the requested city code is already used by
         * another record, excluding the city currently being updated.
         *
         * Example: Mumbai (ID 3) currently has code BOM.
         *
         * BOM → BOM:
         * Allowed because BOM belongs to the same record (ID 3).
         * This is common with PUT because unchanged fields are sent again.
         *
         * BOM → BLR:
         * Check whether BLR is used by any record other than ID 3.
         * If Bengaluru already uses BLR, reject the update to prevent
         * two cities from having the same city code.
         */
        if (cityRepository.existsByCityCodeAndIdNot(
                request.getCityCode(),
                id)) {

            throw new CityAlreadyExistsException(
                    request.getCityCode()
            );
        }


        // Apply request values to the existing entity and save.
        City updatedCity = cityRepository.save(
                CityMapper.updateEntity(city, request)
        );


        log.info(
                "City updated: id={}, name={}, code={}",
                updatedCity.getId(),
                updatedCity.getName(),
                updatedCity.getCityCode()
        );


        /*
         * The returned response is also written to cities::{id}
         * by @CachePut after successful method execution.
         */
        return CityMapper.toResponse(updatedCity);
    }


    // ==================== DELETE ====================

    /**
     * Deletes a city by ID.
     *
     * Related cache entries are removed so deleted city data
     * cannot be returned from Redis.
     */
    @Override

    /*
     * Keeps the existence check and database delete operation
     * inside one transaction.
     */
    @Transactional
    @Caching(evict = {

            // Remove the city cached by ID.
            @CacheEvict(
                    cacheNames = "cities",
                    key = "#id"
            ),

            // Clear code-based cached results.
            @CacheEvict(
                    cacheNames = "citiesByCode",
                    allEntries = true
            )
    })
    public void deleteCity(Long id)
            throws ResourceNotFoundException {


        // Verify that the city exists before deleting it.
        City city = cityRepository
                .findById(id)
                .orElseThrow(() ->
                        new CityNotFoundException(id)
                );


        cityRepository.delete(city);


        log.info(
                "City deleted: id={}, name={}, code={}",
                city.getId(),
                city.getName(),
                city.getCityCode()
        );
    }


    // ==================== SEARCH & QUERY ====================

    /**
     * Searches cities across searchable fields and returns
     * paginated results.
     */
    @Override
    public Page<CityResponse> searchCities(
            String keyword,
            Pageable pageable) {

        log.debug(
                "Searching cities: keyword={}, page={}, size={}",
                keyword,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );


        return cityRepository
                .searchByKeyword(keyword, pageable)
                .map(CityMapper::toResponse);
    }


    /**
     * Returns paginated cities belonging to a country.
     */
    @Override
    public Page<CityResponse> getCitiesByCountryCode(
            String countryCode,
            Pageable pageable) {

        log.debug(
                "Fetching cities by countryCode={}, page={}, size={}",
                countryCode,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );


        return cityRepository
                .findByCountryCodeIgnoreCase(
                        countryCode,
                        pageable
                )
                .map(CityMapper::toResponse);
    }


    // ==================== VALIDATION ====================

    /**
     * Checks whether a city with the given code exists.
     */
    @Override
    public boolean cityExists(String cityCode) {

        log.debug(
                "Checking city existence for code={}",
                cityCode
        );


        return cityRepository
                .existsByCityCode(cityCode);
    }


    /**
     * Validates city-code format.
     *
     * Rules:
     * - 2 to 10 characters
     * - uppercase letters and numbers only
     *
     * Examples: DEL, BLR, CITY01
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
    private void validateCityRequest(
            CityRequest request) {

        validateCityRequest(request, null);
    }


    /**
     * Validates city code, country code, and optional timezone offset.
     *
     * Note: excludeId is currently unused here. Duplicate-code checking
     * during update is handled separately by existsByCityCodeAndIdNot().
     */
    private void validateCityRequest(
            CityRequest request,
            Long excludeId) {


        // City code: 2-10 uppercase letters or numbers.
        if (!validateCityCode(
                request.getCityCode())) {

            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Invalid city code format. Must be 2-10 alphanumeric characters."
            );
        }


        // Country code: 2-5 uppercase letters.
        if (request.getCountryCode() == null
                || !request.getCountryCode()
                .matches("[A-Z]{2,5}")) {

            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Country code must be 2-5 uppercase letters."
            );
        }


        // Optional offset format: +05:30, -04:00, etc.
        if (request.getTimeZoneOffset() != null
                && !request.getTimeZoneOffset()
                .matches("[+-]\\d{2}:\\d{2}")) {

            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Time zone offset must be in format ±HH:MM."
            );
        }
    }
}