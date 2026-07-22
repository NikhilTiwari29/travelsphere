package com.nikhil.services.service.impl;

import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.services.exception.AirportAlreadyExistsException;
import com.nikhil.services.exception.AirportNotFoundException;
import com.nikhil.services.exception.CityNotFoundException;
import com.nikhil.services.mapper.AirportMapper;
import com.nikhil.services.model.Airport;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.AirportRepository;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.AirportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
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

    /*
     * Runs validation, city lookup, relationship setup, and save
     * within one database transaction.
     */
    @Transactional
    public AirportResponse createAirport(AirportRequest request) {

        // Prevent two airports from having the same IATA code.
        if (airportRepository
                .findByIataCode(request.getIataCode())
                .isPresent()) {

            throw new AirportAlreadyExistsException(
                    request.getIataCode()
            );
        }


        // Verify that the city selected for the airport exists.
        City city = cityRepository
                .findById(request.getCityId())
                .orElseThrow(() ->
                        new CityNotFoundException(
                                request.getCityId()
                        )
                );


        // Convert request to entity and establish Airport → City relationship.
        Airport airport = AirportMapper.toEntity(request);
        airport.setCity(city);


        // Save the airport and return the response DTO.
        Airport savedAirport =
                airportRepository.save(airport);


        log.info(
                "Airport created: id={}, IATA code={}, cityId={}",
                savedAirport.getId(),
                savedAirport.getIataCode(),
                city.getId()
        );


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

    /*
     * Runs the bulk operation in one transaction.
     *
     * Records handled with continue are intentionally skipped.
     * An unhandled runtime exception can roll back the transaction.
     */
    @Transactional
    public List<AirportResponse> createBulkAirports(
            List<AirportRequest> requests) {

        // Stores successfully created airports.
        List<AirportResponse> createdAirports =
                new ArrayList<>();

        // Stores skipped airport codes and reasons.
        List<String> skippedCodes =
                new ArrayList<>();


        for (AirportRequest request : requests) {


            // Skip if the IATA code already exists.
            if (airportRepository
                    .findByIataCode(request.getIataCode())
                    .isPresent()) {

                skippedCodes.add(
                        request.getIataCode()
                                + " (already exists)"
                );

                continue;
            }


            // Find the city the airport should belong to.
            Optional<City> cityOpt =
                    cityRepository.findById(
                            request.getCityId()
                    );


            // Skip if the referenced city does not exist.
            if (cityOpt.isEmpty()) {

                skippedCodes.add(
                        request.getIataCode()
                                + " (city not found with id: "
                                + request.getCityId()
                                + ")"
                );

                continue;
            }


            // Convert request into an Airport entity.
            Airport airport =
                    AirportMapper.toEntity(request);


            // Establish the Airport → City relationship.
            airport.setCity(cityOpt.get());


            // Save and collect the successfully created airport.
            Airport savedAirport =
                    airportRepository.save(airport);

            createdAirports.add(
                    AirportMapper.toResponse(savedAirport)
            );
        }


        if (!skippedCodes.isEmpty()) {

            log.info(
                    "Bulk airport creation skipped: {}",
                    skippedCodes
            );
        }


        log.info(
                "Bulk airport creation completed: created {} of {} airports",
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

    /*
     * Keeps entity loading and DTO mapping inside a read-only
     * persistence context while avoiding unnecessary update tracking.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "airports",
            key = "#id"
    )
    public AirportResponse getAirportById(Long id) {

        /*
         * This log runs only on a cache miss because @Cacheable
         * skips the method when Redis already contains airports::{id}.
         */
        log.debug(
                "Cache miss for airport id={}; fetching from database",
                id
        );


        Airport airport = airportRepository
                .findById(id)
                .orElseThrow(() ->
                        new AirportNotFoundException(id)
                );


        return AirportMapper.toResponse(airport);
    }


    /**
     * Returns all airports.
     *
     * The complete list is cached because airport reference data
     * changes much less frequently than it is read.
     */
    @Override

    /*
     * Executes the query and DTO mapping in a read-only transaction
     * because this method does not modify database state.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "allAirports")
    public List<AirportResponse> getAllAirports() {

        /*
         * Runs only when the allAirports cache does not contain
         * the result.
         */
        log.debug(
                "Cache miss for allAirports; fetching airports from database"
        );


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
     * 2. Prevents duplicate IATA codes.
     * 3. Validates and updates the city relationship.
     * 4. Applies remaining request fields and saves the airport.
     *
     * The individual airport cache is updated directly, while related
     * collection and query caches are cleared because they may be stale.
     */
    @Override

    /*
     * Keeps lookup, validation, relationship changes, and database
     * update within one transaction.
     */
    @Transactional
    @Caching(

            /*
             * Replace airports::{id} with the AirportResponse returned
             * by this method, avoiding another DB call on the next GET.
             */
            put = {
                    @CachePut(
                            cacheNames = "airports",
                            key = "#id"
                    )
            },

            /*
             * Derived and collection caches are cleared because the
             * airport's IATA code, city, or other data may have changed.
             */
            evict = {

                    @CacheEvict(
                            cacheNames = "allAirports",
                            allEntries = true
                    ),

                    @CacheEvict(
                            cacheNames = "airportsByIata",
                            allEntries = true
                    ),

                    @CacheEvict(
                            cacheNames = "airportsByCity",
                            allEntries = true
                    )
            }
    )
    public AirportResponse updateAirport(
            Long id,
            AirportRequest request) {


        // Find the airport being updated.
        Airport existingAirport =
                airportRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new AirportNotFoundException(id)
                        );


        /*
         * Check for an IATA-code conflict only when the requested
         * code differs from the airport's current code.
         *
         * Example:
         * Airport ID 3 currently uses BOM.
         *
         * BOM → BOM:
         * Allowed because the airport keeps its own code.
         *
         * BOM → BLR:
         * Check whether another airport already uses BLR.
         * If yes, reject the update to preserve uniqueness.
         */
        if (request.getIataCode() != null
                && !existingAirport
                .getIataCode()
                .equals(request.getIataCode())

                && airportRepository
                .findByIataCode(request.getIataCode())
                .isPresent()) {

            throw new AirportAlreadyExistsException(
                    request.getIataCode()
            );
        }


        /*
         * If cityId is provided, verify that the city exists
         * before changing the airport's city relationship.
         */
        if (request.getCityId() != null) {

            City newCity = cityRepository
                    .findById(request.getCityId())
                    .orElseThrow(() ->
                            new CityNotFoundException(
                                    request.getCityId()
                            )
                    );


            // Update the Airport → City relationship.
            existingAirport.setCity(newCity);
        }


        // Apply remaining request fields to the existing entity.
        AirportMapper.updateEntity(
                request,
                existingAirport
        );


        // Persist the updated airport.
        Airport updatedAirport =
                airportRepository.save(existingAirport);


        log.info(
                "Airport updated: id={}, IATA code={}, cityId={}",
                updatedAirport.getId(),
                updatedAirport.getIataCode(),
                updatedAirport.getCity().getId()
        );


        /*
         * This returned AirportResponse is also written into
         * the airports::{id} cache by @CachePut.
         */
        return AirportMapper.toResponse(updatedAirport);
    }


    // ==================== DELETE ====================

    /**
     * Deletes an airport by ID.
     *
     * Related Redis caches are cleared so deleted or stale airport
     * data cannot be returned from cache.
     */
    @Override

    /*
     * Performs the existence check and deletion within the same
     * database transaction.
     */
    @Transactional
    @Caching(evict = {

            // Remove the individual airport entry.
            @CacheEvict(
                    cacheNames = "airports",
                    key = "#id"
            ),

            // Clear the complete airport list.
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
    public void deleteAirport(Long id) {


        // Verify that the airport exists before deleting it.
        Airport airport = airportRepository
                .findById(id)
                .orElseThrow(() ->
                        new AirportNotFoundException(id)
                );


        airportRepository.delete(airport);


        log.info(
                "Airport deleted: id={}, IATA code={}",
                airport.getId(),
                airport.getIataCode()
        );
    }


    // ==================== QUERY BY CITY ====================

    /**
     * Returns all airports belonging to a city.
     *
     * Results are cached using cityId because one city may contain
     * multiple airports.
     */
    @Override

    /*
     * Keeps entity loading and DTO mapping inside a read-only
     * persistence context while avoiding unnecessary update tracking.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "airportsByCity",
            key = "#cityId"
    )
    public List<AirportResponse> getAirportsByCityId(
            Long cityId) {

        /*
         * Runs only when airportsByCity::{cityId} is not available
         * in Redis.
         */
        log.debug(
                "Cache miss for airportsByCity cityId={}; fetching from database",
                cityId
        );


        return airportRepository
                .findByCityId(cityId)
                .stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }
}