package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightResponse;
import com.nikhil.services.client.AirlineClient;
import com.nikhil.services.client.LocationClient;
import com.nikhil.services.mapper.FlightMapper;
import com.nikhil.services.model.Flight;
import com.nikhil.services.repository.FlightRepository;
import com.nikhil.services.service.FlightService;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * Manages Flight route definitions for airline operations.
 *
 * A Flight represents a reusable route definition and not a specific
 * dated flight occurrence.
 *
 * Example:
 *
 * Flight:
 *   6E201 → DEL to BOM
 *
 * FlightInstances:
 *   6E201 → 2026-07-10
 *   6E201 → 2026-07-11
 *
 * Request Flow
 * ------------
 * FlightController
 *       ↓
 * FlightServiceImpl
 *       ↓
 * FlightRepository
 *
 * Cross-service communication:
 *
 * Airline Core Service
 *   - Resolve airline from authenticated owner.
 *   - Validate aircraft.
 *   - Fetch airline and aircraft details for response enrichment.
 *
 * Location Service
 *   - Fetch departure and arrival airport details.
 *
 * Main responsibilities:
 * - Create single and bulk Flight definitions.
 * - Retrieve Flights by ID, flight number, or airline.
 * - Update Flight definitions and operational status.
 * - Delete Flight definitions.
 * - Batch-fetch and enrich Flights for Booking Service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;


    // ==================== Create Operations ====================

    /**
     * Creates a Flight route definition for the airline owned by the
     * authenticated user.
     *
     * Flow:
     * Duplicate flight-number check
     * → Resolve Airline
     * → Validate Aircraft
     * → Build Flight
     * → Save Flight
     * → Enrich response with cross-service data.
     */
    @Override
    public FlightResponse createFlight(
            Long userId,
            FlightRequest request
    ) {

        log.info(
                "Creating flight userId={} flightNumber={} aircraftId={} departureAirportId={} arrivalAirportId={}",
                userId,
                request.getFlightNumber(),
                request.getAircraftId(),
                request.getDepartureAirportId(),
                request.getArrivalAirportId()
        );

        /*
         * Flight numbers must be unique within the current Flight data model.
         */
        if (flightRepository.existsByFlightNumber(
                request.getFlightNumber()
        )) {

            log.warn(
                    "Flight creation rejected: duplicate flightNumber={}",
                    request.getFlightNumber()
            );

            throw new IllegalArgumentException(
                    "Flight with number '"
                            + request.getFlightNumber()
                            + "' already exists"
            );
        }

        /*
         * Resolve the airline owned by the authenticated user.
         */
        Long airlineId =
                getAirlineForUser(userId);

        log.debug(
                "Airline resolved for flight creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Verify that the referenced aircraft exists in Airline Core Service.
         */
        validateAircraftExists(
                request.getAircraftId()
        );

        Flight flight =
                FlightMapper.toEntity(request);

        flight.setAirlineId(airlineId);

        Flight saved =
                flightRepository.save(flight);

        log.info(
                "Flight created successfully flightId={} flightNumber={} airlineId={} aircraftId={}",
                saved.getId(),
                saved.getFlightNumber(),
                airlineId,
                saved.getAircraftId()
        );

        return getFlightResponse(saved);
    }


    /**
     * Creates multiple Flight route definitions for the authenticated
     * airline owner.
     *
     * Flow:
     * Resolve Airline once
     * → Load existing flight numbers in one DB query
     * → Skip duplicate flight numbers
     * → Validate each unique Aircraft once
     * → Save Flights in bulk
     * → Enrich responses using request-level lookup caches.
     */
    @SneakyThrows
    @Override
    public List<FlightResponse> createFlights(
            Long userId,
            List<FlightRequest> requests
    ) throws AirportException {

        log.info(
                "Bulk flight creation started userId={} requestedCount={}",
                userId,
                requests.size()
        );

        /*
         * Resolve the airline once for the complete bulk operation.
         */
        Long airlineId =
                getAirlineForUser(userId);

        log.debug(
                "Airline resolved for bulk flight creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Find all flight numbers that already exist using one database query.
         *
         * This avoids executing one exists query for every request.
         */
        Set<String> existingNumbers =
                flightRepository.findExistingFlightNumbers(
                        requests.stream()
                                .map(FlightRequest::getFlightNumber)
                                .collect(Collectors.toList())
                );

        log.debug(
                "Existing flight numbers resolved requestedCount={} existingCount={}",
                requests.size(),
                existingNumbers.size()
        );

        /*
         * Track aircraft IDs already validated during this bulk request.
         *
         * If multiple Flights use the same aircraft, Airline Core Service
         * is called only once for that aircraft.
         */
        Set<Long> validatedAircraftIds =
                new HashSet<>();

        List<Flight> toSave =
                requests.stream()

                        /*
                         * Skip Flight numbers already present in the database.
                         */
                        .filter(request -> {

                            boolean exists =
                                    existingNumbers.contains(
                                            request.getFlightNumber()
                                    );

                            if (exists) {

                                log.warn(
                                        "Skipping duplicate flight during bulk creation flightNumber={}",
                                        request.getFlightNumber()
                                );
                            }

                            return !exists;
                        })

                        .map(request -> {

                            /*
                             * Validate each unique aircraft only once during
                             * the complete bulk operation.
                             */
                            if (validatedAircraftIds.add(
                                    request.getAircraftId()
                            )) {

                                log.debug(
                                        "Validating aircraft for bulk flight creation aircraftId={}",
                                        request.getAircraftId()
                                );

                                validateAircraftExists(
                                        request.getAircraftId()
                                );
                            }

                            Flight flight =
                                    FlightMapper.toEntity(request);

                            flight.setAirlineId(airlineId);

                            return flight;
                        })

                        .collect(Collectors.toList());

        List<Flight> saved =
                flightRepository.saveAll(toSave);

        log.info(
                "Flights persisted successfully airlineId={} requestedCount={} createdCount={} skippedCount={}",
                airlineId,
                requests.size(),
                saved.size(),
                requests.size() - saved.size()
        );

        /*
         * Fetch the airline once because every Flight in this bulk request
         * belongs to the same airline.
         */
        AirlineResponse airline =
                airlineClient.getAirlineById(airlineId);

        /*
         * Request-level caches prevent repeated Feign calls while enriching
         * Flights that share the same aircraft or airports.
         */
        Map<Long, AircraftResponse> aircraftCache =
                new HashMap<>();

        Map<Long, AirportResponse> airportCache =
                new HashMap<>();

        List<FlightResponse> responses =
                new ArrayList<>();

        for (Flight flight : saved) {

            AircraftResponse aircraft =
                    aircraftCache.computeIfAbsent(
                            flight.getAircraftId(),
                            airlineClient::getAircraftById
                    );

            AirportResponse departure =
                    airportCache.computeIfAbsent(
                            flight.getDepartureAirportId(),
                            this::getAirport
                    );

            AirportResponse arrival =
                    airportCache.computeIfAbsent(
                            flight.getArrivalAirportId(),
                            this::getAirport
                    );

            responses.add(
                    FlightMapper.toResponse(
                            flight,
                            aircraft,
                            airline,
                            departure,
                            arrival
                    )
            );
        }

        log.info(
                "Bulk flight creation completed airlineId={} createdCount={} uniqueAircraftLookups={} uniqueAirportLookups={}",
                airlineId,
                responses.size(),
                aircraftCache.size(),
                airportCache.size()
        );

        return responses;
    }


    // ==================== Read Operations ====================

    /**
     * Returns a Flight by its database identifier and enriches the response
     * with airline, aircraft, departure airport, and arrival airport details.
     */
    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlightById(Long id) {

        log.debug(
                "Fetching flight flightId={}",
                id
        );

        Flight flight =
                flightRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight lookup failed: flightId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with id: " + id
                            );
                        });

        log.debug(
                "Flight retrieved flightId={} flightNumber={}",
                flight.getId(),
                flight.getFlightNumber()
        );

        return getFlightResponse(flight);
    }


    /**
     * Returns a Flight using its flight number and enriches the response
     * with related cross-service data.
     */
    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlightByNumber(
            String flightNumber
    ) throws AirportException {

        log.debug(
                "Fetching flight by flightNumber={}",
                flightNumber
        );

        Flight flight =
                flightRepository.findByFlightNumber(flightNumber)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight lookup failed: flightNumber={} not found",
                                    flightNumber
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with number: "
                                            + flightNumber
                            );
                        });

        log.debug(
                "Flight retrieved flightId={} flightNumber={}",
                flight.getId(),
                flightNumber
        );

        return getFlightResponse(flight);
    }


    /**
     * Returns paginated Flights belonging to the authenticated user's airline.
     *
     * Departure and arrival airport IDs are optional filters.
     *
     * Flow:
     * User → Resolve Airline
     * → Query airline Flights with optional route filters
     * → Enrich each Flight response.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> getFlightsByAirline(
            Long userId,
            Long departureAirportId,
            Long arrivalAirportId,
            Pageable pageable
    ) {

        log.debug(
                "Fetching airline flights userId={} departureAirportId={} arrivalAirportId={} page={} size={}",
                userId,
                departureAirportId,
                arrivalAirportId,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Long airlineId =
                getAirlineForUser(userId);

        Page<FlightResponse> responses =
                flightRepository
                        .findByAirlineIdAndOptionalRoute(
                                airlineId,
                                departureAirportId,
                                arrivalAirportId,
                                pageable
                        )
                        .map(this::getFlightResponse);

        log.debug(
                "Airline flights retrieved airlineId={} returnedCount={} totalElements={}",
                airlineId,
                responses.getNumberOfElements(),
                responses.getTotalElements()
        );

        return responses;
    }


    /**
     * Returns multiple Flights by their database IDs and enriches each Flight
     * with Airline, Aircraft, and Airport details from other microservices.
     *
     * Flow:
     * Load all requested Flights in one database query
     * → Fetch required cross-service data
     * → Reuse already fetched data within this method call
     * → Build enriched FlightResponse objects.
     *
     * Method-local caches are used to avoid repeated Feign calls when multiple
     * Flights reference the same Airline, Aircraft, or Airport.
     *
     * These caches exist only during this method execution. They are not Redis
     * caches or application-level caches.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, FlightResponse> getFlightsByIds(
            List<Long> ids
    ) {

        /*
         * Return immediately when no Flight IDs are provided.
         *
         * This avoids an unnecessary database query and downstream service calls.
         */
        if (ids == null || ids.isEmpty()) {

            log.debug(
                    "Batch flight lookup skipped: no flight IDs provided"
            );

            return Map.of();
        }

        log.debug(
                "Batch flight lookup started requestedCount={}",
                ids.size()
        );

        /*
         * Load all requested Flight records in a single database query.
         *
         * This avoids querying the Flight table separately for every ID.
         */
        List<Flight> flights =
                flightRepository.findAllById(ids);

        log.debug(
                "Flight records loaded for batch lookup requestedCount={} foundCount={}",
                ids.size(),
                flights.size()
        );

        /*
         * Temporary lookup caches used only during this method execution.
         *
         * Multiple Flights may reference the same Airline, Aircraft, or Airport.
         * Once data for an ID is fetched through Feign, it is stored in the
         * corresponding map and reused for the remaining Flights in this batch.
         *
         * Example:
         *
         * Flight 1 → airlineId=10, aircraftId=101, DEL → BOM
         * Flight 2 → airlineId=10, aircraftId=101, DEL → BLR
         *
         * Without these maps:
         * Airline 10 and Aircraft 101 would be fetched twice.
         *
         * With these maps:
         * Each unique ID is fetched only once during this batch request.
         *
         * These are method-local HashMaps, not Redis or Spring caches.
         */
        Map<Long, AirlineResponse> airlineCache =
                new HashMap<>();

        Map<Long, AircraftResponse> aircraftCache =
                new HashMap<>();

        Map<Long, AirportResponse> airportCache =
                new HashMap<>();

        /*
         * Final response map:
         *
         * key   → Flight database ID
         * value → Fully enriched FlightResponse
         */
        Map<Long, FlightResponse> result =
                new HashMap<>();

        for (Flight flight : flights) {

            /*
             * Fetch Airline details only if this airlineId has not already
             * been resolved during the current batch operation.
             *
             * computeIfAbsent():
             *
             * Cache hit  → return existing AirlineResponse.
             * Cache miss → call Airline Service, store response, then return it.
             */
            AirlineResponse airline =
                    airlineCache.computeIfAbsent(
                            flight.getAirlineId(),
                            airlineClient::getAirlineById
                    );

            /*
             * Fetch Aircraft details only once for each unique aircraftId
             * referenced by the Flights in this batch.
             */
            AircraftResponse aircraft =
                    aircraftCache.computeIfAbsent(
                            flight.getAircraftId(),
                            airlineClient::getAircraftById
                    );

            /*
             * Fetch the departure Airport only if it has not already been
             * fetched while processing another Flight in this batch.
             */
            AirportResponse departure =
                    airportCache.computeIfAbsent(
                            flight.getDepartureAirportId(),
                            this::getAirport
                    );

            /*
             * The same Airport cache is shared by departure and arrival lookups.
             *
             * Therefore, if an Airport was previously fetched as a departure
             * Airport and later appears as an arrival Airport, no additional
             * Location Service call is required.
             */
            AirportResponse arrival =
                    airportCache.computeIfAbsent(
                            flight.getArrivalAirportId(),
                            this::getAirport
                    );

            /*
             * Combine the local Flight entity with cross-service data and store
             * the enriched response using the Flight ID as the map key.
             */
            result.put(
                    flight.getId(),
                    FlightMapper.toResponse(
                            flight,
                            aircraft,
                            airline,
                            departure,
                            arrival
                    )
            );
        }

        /*
         * Cache sizes represent the number of unique downstream resources
         * resolved during this batch operation.
         *
         * Example:
         * 20 Flights may result in only:
         * - 1 Airline lookup
         * - 5 Aircraft lookups
         * - 8 Airport lookups
         *
         * depending on how many resources are shared between those Flights.
         */
        log.debug(
                "Batch flight lookup completed requestedCount={} returnedCount={} airlineLookups={} aircraftLookups={} airportLookups={}",
                ids.size(),
                result.size(),
                airlineCache.size(),
                aircraftCache.size(),
                airportCache.size()
        );

        return result;
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing Flight route definition.
     *
     * Flow:
     * Load Flight
     * → Validate flight-number uniqueness
     * → Apply request changes
     * → Save Flight
     * → Enrich response.
     */
    @Override
    public FlightResponse updateFlight(
            Long id,
            FlightRequest request
    ) throws AirportException {

        log.info(
                "Updating flight flightId={} requestedFlightNumber={}",
                id,
                request.getFlightNumber()
        );

        Flight existing =
                flightRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight update failed: flightId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with id: " + id
                            );
                        });

        /*
         * Ensure that the requested flight number is not already assigned
         * to another Flight record.
         *
         * The current Flight ID is excluded from the uniqueness check so that
         * keeping the existing flight number during an update is allowed.
         *
         * Example:
         *
         * Current Flight:
         *   id = 10, flightNumber = "6E201"
         *
         * Updating Flight 10 with "6E201" → allowed
         * Updating Flight 10 with a number used by Flight 20 → rejected
         */
        if (request.getFlightNumber() != null
                && flightRepository.existsByFlightNumberAndIdNot(
                request.getFlightNumber(),
                id
        )) {

            log.warn(
                    "Flight update rejected: duplicate flightNumber={} flightId={}",
                    request.getFlightNumber(),
                    id
            );

            throw new IllegalArgumentException(
                    "Flight with number '"
                            + request.getFlightNumber()
                            + "' already exists"
            );
        }

        FlightMapper.updateEntity(
                request,
                existing
        );

        Flight saved =
                flightRepository.save(existing);

        log.info(
                "Flight updated successfully flightId={} flightNumber={}",
                saved.getId(),
                saved.getFlightNumber()
        );

        return getFlightResponse(saved);
    }


    // ==================== Status Operations ====================

    /**
     * Changes the operational status of a Flight route definition.
     */
    @Override
    public FlightResponse changeStatus(
            Long id,
            FlightStatus status
    ) throws AirportException {

        log.info(
                "Changing flight status flightId={} targetStatus={}",
                id,
                status
        );

        Flight flight =
                flightRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight status change failed: flightId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with id: " + id
                            );
                        });

        FlightStatus previousStatus =
                flight.getStatus();

        flight.setStatus(status);

        log.info(
                "Flight status changed successfully flightId={} previousStatus={} newStatus={}",
                id,
                previousStatus,
                status
        );

        return getFlightResponse(flight);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a Flight route definition by its database identifier.
     */
    @Override
    public void deleteFlight(Long id) {

        log.info(
                "Deleting flight flightId={}",
                id
        );

        Flight flight =
                flightRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight deletion failed: flightId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with id: " + id
                            );
                        });

        flightRepository.delete(flight);

        log.info(
                "Flight deleted successfully flightId={} flightNumber={}",
                id,
                flight.getFlightNumber()
        );
    }


    // ==================== Cross-Service Helper Methods ====================

    /**
     * Fetches Airport details from Location Service.
     *
     * Used directly and as the loader function for request-level airport
     * caches during bulk and batch operations.
     */
    private AirportResponse getAirport(Long id) {

        log.debug(
                "Fetching airport details from location-service airportId={}",
                id
        );

        return locationClient.getAirportById(id);
    }


    /**
     * Resolves the Airline owned by the authenticated user through
     * Airline Core Service.
     *
     * A 404 response is translated into EntityNotFoundException, while
     * other Feign failures are treated as downstream service failures.
     */
    private Long getAirlineForUser(Long userId) {

        log.debug(
                "Resolving airline for user userId={}",
                userId
        );

        try {

            AirlineResponse airline =
                    airlineClient.getAirlineByOwner(userId);

            log.debug(
                    "Airline resolved successfully userId={} airlineId={}",
                    userId,
                    airline.getId()
            );

            return airline.getId();

        } catch (FeignException.NotFound exception) {

            log.warn(
                    "Airline resolution failed: no airline found userId={}",
                    userId
            );

            throw new EntityNotFoundException(
                    "No airline found for user: " + userId
            );

        } catch (FeignException exception) {

            log.error(
                    "Airline resolution failed due to airline-core-service error userId={} status={}",
                    userId,
                    exception.status(),
                    exception
            );

            throw new RuntimeException(
                    "Failed to fetch airline from airline-core-service: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    /**
     * Validates that an Aircraft exists in Airline Core Service.
     *
     * This method performs validation only; the returned AircraftResponse
     * is not required by the calling operation.
     */
    private void validateAircraftExists(Long aircraftId) {

        log.debug(
                "Validating aircraft existence aircraftId={}",
                aircraftId
        );

        try {

            airlineClient.getAircraftById(aircraftId);

            log.debug(
                    "Aircraft validation successful aircraftId={}",
                    aircraftId
            );

        } catch (FeignException.NotFound exception) {

            log.warn(
                    "Aircraft validation failed: aircraftId={} not found",
                    aircraftId
            );

            throw new EntityNotFoundException(
                    "Aircraft not found with id: " + aircraftId
            );

        } catch (FeignException exception) {

            log.error(
                    "Aircraft validation failed due to airline-core-service error aircraftId={} status={}",
                    aircraftId,
                    exception.status(),
                    exception
            );

            throw new RuntimeException(
                    "Failed to validate aircraft from airline-core-service: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    /**
     * Builds a fully enriched FlightResponse.
     *
     * The Flight table stores cross-service IDs. This method resolves those
     * references and combines the data required by the API response.
     *
     * Enrichment flow:
     *
     * Flight.aircraftId
     *      → Airline Core Service
     *
     * Flight.airlineId
     *      → Airline Core Service
     *
     * Flight.departureAirportId
     *      → Location Service
     *
     * Flight.arrivalAirportId
     *      → Location Service
     */
    private FlightResponse getFlightResponse(
            Flight flight
    ) {

        log.debug(
                "Enriching flight response flightId={} flightNumber={}",
                flight.getId(),
                flight.getFlightNumber()
        );

        AircraftResponse aircraft =
                airlineClient.getAircraftById(
                        flight.getAircraftId()
                );

        AirlineResponse airline =
                airlineClient.getAirlineById(
                        flight.getAirlineId()
                );

        AirportResponse departureAirport =
                locationClient.getAirportById(
                        flight.getDepartureAirportId()
                );

        AirportResponse arrivalAirport =
                locationClient.getAirportById(
                        flight.getArrivalAirportId()
                );

        log.debug(
                "Flight response enrichment completed flightId={} airlineId={} aircraftId={} departureAirportId={} arrivalAirportId={}",
                flight.getId(),
                flight.getAirlineId(),
                flight.getAircraftId(),
                flight.getDepartureAirportId(),
                flight.getArrivalAirportId()
        );

        return FlightMapper.toResponse(
                flight,
                aircraft,
                airline,
                departureAirport,
                arrivalAirport
        );
    }
}