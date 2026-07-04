package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.CityException;
import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.services.mapper.AirportMapper;
import com.nikhil.services.model.Airport;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.AirportRepository;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.AirportService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles airport CRUD operations, bulk creation, city association,
 * airport queries, and Redis cache management.
 *
 * Each airport is linked to an existing city through cityId.
 *
 * Data flow:
 * AirportRequest → validate IATA code and City → Airport entity
 * → repository → AirportResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;


    // ==================== CREATE ====================

    /**
     * Creates a new airport after checking that:
     * 1. The IATA code is not already used by another airport.
     * 2. The city referenced by cityId exists.
     *
     * The airport is then linked to the city and saved.
     */
    @Override
    @Transactional
    public AirportResponse createAirport(AirportRequest request)
            throws AirportException, CityException {

        // Prevent two airports from having the same IATA code.
        if (airportRepository.findByIataCode(request.getIataCode()).isPresent()) {

            throw new AirportException(
                    "Airport with IATA code "
                            + request.getIataCode()
                            + " already exists."
            );
        }

        // Verify that the city selected for the airport exists.
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() ->
                        new CityException(
                                "City not found with id: "
                                        + request.getCityId()
                        )
                );

        // Convert the request to an Airport entity and establish the city relationship.
        Airport airport = AirportMapper.toEntity(request);
        airport.setCity(city);

        // Save the airport and return the response DTO.
        Airport savedAirport = airportRepository.save(airport);

        return AirportMapper.toResponse(savedAirport);
    }


    /**
     * Creates multiple airports in a single request.
     *
     * Each request is processed independently:
     * - Existing IATA codes are skipped.
     * - Requests referencing a missing city are skipped.
     * - Valid airports are linked to their city and saved.
     */
    @Override
    @Transactional
    public List<AirportResponse> createBulkAirports(
            List<AirportRequest> requests) {

        // Stores successfully created airports.
        List<AirportResponse> createdAirports = new ArrayList<>();

        // Stores skipped airport codes and the reason for skipping them.
        List<String> skippedCodes = new ArrayList<>();


        for (AirportRequest request : requests) {

            // Skip the airport if its IATA code already exists.
            if (airportRepository
                    .findByIataCode(request.getIataCode())
                    .isPresent()) {

                skippedCodes.add(
                        request.getIataCode() + " (already exists)"
                );

                continue;
            }


            // Find the city that the airport should belong to.
            Optional<City> cityOpt =
                    cityRepository.findById(request.getCityId());


            // Skip the airport if the referenced city does not exist.
            if (cityOpt.isEmpty()) {

                skippedCodes.add(
                        request.getIataCode()
                                + " (city not found with id: "
                                + request.getCityId()
                                + ")"
                );

                continue;
            }


            // Convert the request into an Airport entity.
            Airport airport = AirportMapper.toEntity(request);

            // Establish the Airport → City relationship.
            airport.setCity(cityOpt.get());


            // Save the airport and add it to the successful results.
            Airport savedAirport =
                    airportRepository.save(airport);

            createdAirports.add(
                    AirportMapper.toResponse(savedAirport)
            );
        }


        if (!skippedCodes.isEmpty()) {
            log.info(
                    "Bulk airport creation - skipped: {}",
                    skippedCodes
            );
        }


        log.info(
                "Bulk airport creation - created {} out of {} airports",
                createdAirports.size(),
                requests.size()
        );


        return createdAirports;
    }


    // ==================== READ ====================

    /**
     * Returns an airport by ID.
     *
     * The response is cached in Redis using the airport ID.
     * On a cache hit, the database query is skipped.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "airports",
            key = "#id"
    )
    public AirportResponse getAirportById(Long id) {

        // Executed only when the airport is not available in cache.
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Airport not found with id: " + id
                        )
                );

        return AirportMapper.toResponse(airport);
    }


    /**
     * Returns all airports.
     *
     * The complete airport list is cached in Redis because airport
     * reference data changes less frequently than it is read.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "allAirports")
    public List<AirportResponse> getAllAirports() {

        return airportRepository
                .findAll()
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }


    // ==================== UPDATE ====================

    /**
     * Updates an existing airport.
     *
     * The method:
     * 1. Finds the airport being updated.
     * 2. Prevents changing its IATA code to one used by another airport.
     * 3. Validates and updates the city relationship.
     * 4. Applies the remaining request fields and saves the airport.
     *
     * Related Redis caches are cleared after a successful update.
     */
    @Override
    @Transactional
    @Caching(evict = {

            // Remove the cached airport being updated.
            @CacheEvict(
                    cacheNames = "airports",
                    key = "#id"
            ),

            // Clear the cached airport list because airport data changed.
            @CacheEvict(
                    cacheNames = "allAirports",
                    allEntries = true
            ),

            // Clear IATA-based cache because the IATA code may have changed.
            @CacheEvict(
                    cacheNames = "airportsByIata",
                    allEntries = true
            ),

            // Clear city-based results because the airport's city may have changed.
            @CacheEvict(
                    cacheNames = "airportsByCity",
                    allEntries = true
            )
    })
    public AirportResponse updateAirport(
            Long id,
            AirportRequest request)
            throws AirportException, CityException {

        // Find the existing airport that needs to be updated.
        Airport existingAirport = airportRepository.findById(id)
                .orElseThrow(() ->
                        new AirportException(
                                "Airport not found with id: " + id
                        )
                );


        /*
         * Check for an IATA-code conflict only when the request contains
         * a different code from the airport's current code.
         *
         * Example:
         * Suppose Mumbai Airport (ID 3) currently has IATA code BOM.
         *
         * - Request contains BOM:
         *   No duplicate check is required because the airport is keeping
         *   its own existing code.
         *
         * - Request changes BOM to BLR:
         *   Check whether BLR already belongs to another airport.
         *   If Bengaluru Airport already uses BLR, reject the update to
         *   prevent two airports from having the same IATA code.
         */
        if (request.getIataCode() != null
                && !existingAirport.getIataCode()
                .equals(request.getIataCode())
                && airportRepository
                .findByIataCode(request.getIataCode())
                .isPresent()) {

            throw new AirportException(
                    "IATA code "
                            + request.getIataCode()
                            + " is already taken."
            );
        }


        /*
         * If a cityId is provided, verify that the city exists before
         * changing the airport's city relationship.
         *
         * Example:
         * If an airport is moved from cityId 3 to cityId 5,
         * city ID 5 must exist before the relationship is updated.
         */
        if (request.getCityId() != null) {

            City newCity = cityRepository
                    .findById(request.getCityId())
                    .orElseThrow(() ->
                            new CityException(
                                    "City not found with id: "
                                            + request.getCityId()
                            )
                    );

            // Update the Airport → City relationship.
            existingAirport.setCity(newCity);
        }


        // Apply remaining request fields to the existing airport entity.
        AirportMapper.updateEntity(
                request,
                existingAirport
        );


        // Persist the updated airport.
        Airport updatedAirport =
                airportRepository.save(existingAirport);


        return AirportMapper.toResponse(updatedAirport);
    }


    // ==================== DELETE ====================

    /**
     * Deletes an airport by ID.
     *
     * Related Redis cache entries are cleared so deleted airport data
     * cannot be returned from cache.
     */
    @Override
    @Transactional
    @Caching(evict = {

            // Remove the individual airport cache entry.
            @CacheEvict(
                    cacheNames = "airports",
                    key = "#id"
            ),

            // Clear the cached list of all airports.
            @CacheEvict(
                    cacheNames = "allAirports",
                    allEntries = true
            ),

            // Clear IATA-based cached results.
            @CacheEvict(
                    cacheNames = "airportsByIata",
                    allEntries = true
            ),

            // Clear city-based cached results.
            @CacheEvict(
                    cacheNames = "airportsByCity",
                    allEntries = true
            )
    })
    public void deleteAirport(Long id)
            throws AirportException {

        // Verify that the airport exists before deleting it.
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() ->
                        new AirportException(
                                "Airport not found with id: " + id
                        )
                );

        airportRepository.delete(airport);
    }


    // ==================== QUERY BY CITY ====================

    /**
     * Returns all airports belonging to a city.
     *
     * Results are cached using cityId as the key because a city
     * may contain multiple airports.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "airportsByCity",
            key = "#cityId"
    )
    public List<AirportResponse> getAirportsByCityId(Long cityId) {

        return airportRepository
                .findByCityId(cityId)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }
}