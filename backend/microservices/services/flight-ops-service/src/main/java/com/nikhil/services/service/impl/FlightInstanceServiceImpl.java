package com.nikhil.services.service.impl;

import com.nikhil.common_lib.event.FlightInstanceCreatedEvent;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.client.AirlineClient;
import com.nikhil.services.client.LocationClient;
import com.nikhil.services.event.FlightInstanceEventProducer;
import com.nikhil.services.exception.AircraftNotFoundException;
import com.nikhil.services.exception.AirlineNotFoundException;
import com.nikhil.services.exception.FlightInstanceNotFoundException;
import com.nikhil.services.exception.FlightNotFoundException;
import com.nikhil.services.mapper.FlightInstanceMapper;
import com.nikhil.services.model.Flight;
import com.nikhil.services.model.FlightInstance;
import com.nikhil.services.repository.FlightInstanceRepository;
import com.nikhil.services.repository.FlightRepository;
import com.nikhil.services.service.FlightInstanceService;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Manages the lifecycle of concrete, dated FlightInstance records.
 *
 * A Flight defines the reusable route configuration, while a FlightInstance
 * represents one actual occurrence of that Flight on a specific date and time.
 *
 * Example:
 *
 * Flight:
 *   6E201 → DEL to BOM
 *
 * FlightInstances:
 *   6E201 → 2026-07-10 10:00
 *   6E201 → 2026-07-12 10:00
 *   6E201 → 2026-07-14 10:00
 *
 * Creation Flow
 * -------------
 * Resolve Airline from authenticated user
 *          ↓
 * Load parent Flight
 *          ↓
 * Fetch Aircraft capacity
 *          ↓
 * Create and persist FlightInstance
 *          ↓
 * Publish FlightInstanceCreatedEvent
 *          ↓
 * Seat Service provisions runtime cabin and seat inventory
 *
 * Cross-service communication:
 *
 * Airline Core Service
 *   - Resolve airline ownership.
 *   - Fetch Airline and Aircraft details.
 *
 * Location Service
 *   - Fetch departure and arrival Airport details.
 *
 * Seat Service
 *   - Consumes FlightInstanceCreatedEvent and provisions runtime inventory.
 *
 * Booking Service
 *   - Uses the batch lookup API for booking-detail enrichment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightInstanceServiceImpl implements FlightInstanceService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final FlightRepository flightRepository;
    private final AirlineClient airlineClient;
    private final FlightInstanceEventProducer flightInstanceEventProducer;
    private final LocationClient locationClient;


    // ==================== Create Operations ====================

    /**
     * Creates one concrete FlightInstance and publishes an event for
     * downstream runtime seat-inventory provisioning.
     *
     * Flow:
     * User → Airline
     * → Flight
     * → Aircraft capacity
     * → Save FlightInstance
     * → Publish FlightInstanceCreatedEvent
     * → Build enriched response.
     *
     * rollbackFor = Exception.class is used because this method declares
     * checked exceptions in addition to runtime exceptions.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            cacheNames = "flightInstances",
            allEntries = true
    )
    public FlightInstanceResponse createFlightInstanceWithCabins(
            Long userId,
            FlightInstanceRequest request
    ) throws Exception {

        log.info(
                "Creating flight instance userId={} flightId={} scheduleId={} departureDateTime={}",
                userId,
                request.getFlightId(),
                request.getScheduleId(),
                request.getDepartureDateTime()
        );

        /*
         * Resolve the Airline owned by the authenticated user.
         *
         * The resolved Airline ID is stored on the FlightInstance so that
         * airline-specific queries can be performed without a remote call.
         */
        Long airlineId =
                getAirlineForUser(userId);

        log.debug(
                "Airline resolved for flight instance creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Load the parent Flight definition.
         *
         * The Flight provides route-level configuration such as the assigned
         * Aircraft and represents the reusable route definition from which
         * this dated FlightInstance is created.
         */
        Flight flight =
                flightRepository.findById(
                                request.getFlightId()
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance creation failed: flight not found flightId={}",
                                    request.getFlightId()
                            );

                            return new FlightNotFoundException(request.getFlightId());
                        });

        log.debug(
                "Flight loaded for instance creation flightId={} flightNumber={} aircraftId={}",
                flight.getId(),
                flight.getFlightNumber(),
                flight.getAircraftId()
        );

        /*
         * Fetch Aircraft details to obtain the total configured capacity.
         *
         * The FlightInstance starts with the complete Aircraft capacity
         * available before any runtime seat inventory is booked.
         */
        AircraftResponse aircraft =
                getAircraftById(
                        flight.getAircraftId()
                );

        log.debug(
                "Aircraft resolved for flight instance creation aircraftId={} totalSeats={}",
                flight.getAircraftId(),
                aircraft.getTotalSeats()
        );

        /*
         * Convert the request into a concrete dated FlightInstance and attach
         * the resolved ownership, route, and capacity information.
         */
        FlightInstance instance =
                FlightInstanceMapper.toEntity(
                        request,
                        flight
                );

        instance.setAirlineId(airlineId);
        instance.setFlight(flight);

        instance.setDepartureAirportId(
                request.getDepartureAirportId()
        );

        instance.setArrivalAirportId(
                request.getArrivalAirportId()
        );

        instance.setTotalSeats(
                aircraft.getTotalSeats()
        );

        instance.setAvailableSeats(
                aircraft.getTotalSeats()
        );

        FlightInstance flightInstance =
                flightInstanceRepository.save(instance);

        log.info(
                "Flight instance persisted successfully flightInstanceId={} flightId={} airlineId={} aircraftId={} totalSeats={}",
                flightInstance.getId(),
                flight.getId(),
                airlineId,
                flight.getAircraftId(),
                aircraft.getTotalSeats()
        );

        /*
         * Publish an event after the FlightInstance has been persisted and its
         * database ID is available.
         *
         * Seat Service consumes this event and uses the Aircraft configuration
         * to provision runtime FlightInstanceCabin and SeatInstance records for
         * this exact dated flight occurrence.
         */
        log.info(
                "Publishing flight instance created event flightInstanceId={} flightId={} aircraftId={}",
                flightInstance.getId(),
                flight.getId(),
                flight.getAircraftId()
        );

        flightInstanceEventProducer.sendFlightInstanceCreated(
                FlightInstanceCreatedEvent.builder()
                        .flightInstanceId(
                                flightInstance.getId()
                        )
                        .aircraftId(
                                flight.getAircraftId()
                        )
                        .flightId(
                                flight.getId()
                        )
                        .build()
        );

        log.info(
                "Flight instance created event published successfully flightInstanceId={} flightId={} aircraftId={}",
                flightInstance.getId(),
                flight.getId(),
                flight.getAircraftId()
        );

        /*
         * Enrich the persisted FlightInstance with Airline, Aircraft,
         * and Airport information before returning the API response.
         */
        return getFlightInstance(
                flightInstance
        );
    }


    // ==================== Read Operations ====================

    /**
     * Returns all FlightInstances and enriches each record with related
     * Airline, Aircraft, and Airport information.
     *
     * This performs one enrichment flow per FlightInstance. For large data
     * volumes, the paginated airline-specific query should be preferred.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlightInstanceResponse> getFlightInstances() {

        log.debug(
                "Fetching all flight instances"
        );

        List<FlightInstance> instances =
                flightInstanceRepository.findAll();

        log.debug(
                "Flight instances loaded instanceCount={}",
                instances.size()
        );

        List<FlightInstanceResponse> responses =
                instances.stream()
                        .map(this::getFlightInstance)
                        .toList();

        log.debug(
                "All flight instances lookup completed returnedCount={}",
                responses.size()
        );

        return responses;
    }


    /**
     * Returns one FlightInstance by its database identifier.
     *
     * The result is cached by FlightInstance ID to avoid repeating the
     * database lookup and cross-service response-enrichment calls.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "flightInstances",
            key = "#id"
    )
    public FlightInstanceResponse getFlightInstanceById(
            Long id
    ) {

        log.debug(
                "Fetching flight instance flightInstanceId={}",
                id
        );

        FlightInstance flightInstance =
                flightInstanceRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance lookup failed: flightInstanceId={} not found",
                                    id
                            );

                            return new FlightInstanceNotFoundException(id);
                        });

        log.debug(
                "Flight instance loaded flightInstanceId={} flightId={}",
                flightInstance.getId(),
                flightInstance.getFlight().getId()
        );

        return getFlightInstance(
                flightInstance
        );
    }


    /**
     * Returns paginated FlightInstances belonging to the authenticated
     * user's Airline.
     *
     * Optional filters support:
     * - departure Airport
     * - arrival Airport
     * - Flight ID
     * - exact operating date
     *
     * When onDate is provided, it is converted into a half-open time range:
     *
     * [start of requested date, start of next date)
     *
     * Example:
     *
     * onDate = 2026-07-10
     *
     * start = 2026-07-10T00:00
     * end   = 2026-07-11T00:00
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FlightInstanceResponse> getByAirlineId(
            Long userId,
            Long departureAirportId,
            Long arrivalAirportId,
            Long flightId,
            LocalDate onDate,
            Pageable pageable
    ) {

        log.debug(
                "Fetching airline flight instances userId={} departureAirportId={} arrivalAirportId={} flightId={} onDate={} page={} size={}",
                userId,
                departureAirportId,
                arrivalAirportId,
                flightId,
                onDate,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Long airlineId =
                getAirlineForUser(userId);

        /*
         * Convert an optional LocalDate filter into a date-time range so the
         * repository can match all FlightInstances occurring during that day.
         */
        LocalDateTime start =
                onDate != null
                        ? onDate.atStartOfDay()
                        : null;

        LocalDateTime end =
                onDate != null
                        ? onDate.plusDays(1).atStartOfDay()
                        : null;

        log.debug(
                "Airline resolved for flight instance lookup userId={} airlineId={} dateRangeStart={} dateRangeEnd={}",
                userId,
                airlineId,
                start,
                end
        );

        Page<FlightInstanceResponse> responses =
                flightInstanceRepository
                        .findByAirlineIdWithFilters(
                                airlineId,
                                departureAirportId,
                                arrivalAirportId,
                                flightId,
                                start,
                                end,
                                pageable
                        )
                        .map(this::getFlightInstance);

        log.debug(
                "Airline flight instance lookup completed airlineId={} returnedCount={} totalElements={} totalPages={}",
                airlineId,
                responses.getNumberOfElements(),
                responses.getTotalElements(),
                responses.getTotalPages()
        );

        return responses;
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing FlightInstance.
     *
     * The cached response for this FlightInstance ID is evicted so that the
     * next read returns the updated database state and refreshed enrichment
     * data.
     */
    @Override
    @Transactional
    @CacheEvict(
            cacheNames = "flightInstances",
            key = "#id"
    )
    public FlightInstanceResponse updateFlightInstance(
            Long id,
            FlightInstanceRequest request
    ) {

        log.info(
                "Updating flight instance flightInstanceId={} flightId={} departureDateTime={} arrivalDateTime={}",
                id,
                request.getFlightId(),
                request.getDepartureDateTime(),
                request.getArrivalDateTime()
        );

        FlightInstance existing =
                flightInstanceRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance update failed: flightInstanceId={} not found",
                                    id
                            );

                            return new FlightInstanceNotFoundException(id);
                        });

        /*
         * Apply mutable runtime fields from the request to the existing
         * persistent FlightInstance entity.
         */
        FlightInstanceMapper.updateEntity(
                request,
                existing
        );

        FlightInstance saved =
                flightInstanceRepository.save(existing);

        log.info(
                "Flight instance updated successfully flightInstanceId={} flightId={}",
                saved.getId(),
                saved.getFlight().getId()
        );

        return getFlightInstance(
                saved
        );
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes an existing FlightInstance.
     *
     * The cached response for the deleted FlightInstance ID is also removed.
     */
    @Override
    @Transactional
    @CacheEvict(
            cacheNames = "flightInstances",
            key = "#id"
    )
    public void deleteFlightInstance(
            Long id
    ) {

        log.info(
                "Deleting flight instance flightInstanceId={}",
                id
        );

        FlightInstance flightInstance =
                flightInstanceRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance deletion failed: flightInstanceId={} not found",
                                    id
                            );

                            return new FlightInstanceNotFoundException(id);
                        });

        flightInstanceRepository.delete(
                flightInstance
        );

        log.info(
                "Flight instance deleted successfully flightInstanceId={}",
                id
        );
    }


    // ==================== Batch Operations ====================

    /**
     * Returns multiple FlightInstances by ID and enriches them with Airline,
     * Aircraft, and Airport information.
     *
     * Flow:
     * Load all requested FlightInstances in one database query
     * → Fetch required cross-service data
     * → Reuse already fetched data within this method call
     * → Build enriched FlightInstanceResponse map.
     *
     * Method-local lookup maps avoid repeated Feign calls when multiple
     * FlightInstances reference the same Airline, Aircraft, or Airport.
     *
     * These caches exist only during this method execution. They are not
     * Redis caches or Spring Cache entries.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, FlightInstanceResponse> getFlightInstancesByIds(
            List<Long> ids
    ) {

        /*
         * Return immediately when no FlightInstance IDs are provided.
         *
         * This avoids an unnecessary database query and downstream calls.
         */
        if (ids == null || ids.isEmpty()) {

            log.debug(
                    "Batch flight instance lookup skipped: no IDs provided"
            );

            return Map.of();
        }

        log.debug(
                "Batch flight instance lookup started requestedCount={}",
                ids.size()
        );

        /*
         * Fetch all requested FlightInstances together with their associated
         * Flight entities in a single query to avoid N+1 database queries
         * during batch response mapping.
         */
        List<FlightInstance> instances =
                flightInstanceRepository.findAllByIdInWithFlight(ids);

        log.debug(
                "Flight instances loaded for batch lookup requestedCount={} foundCount={}",
                ids.size(),
                instances.size()
        );

        /*
         * Temporary request-level lookup caches.
         *
         * Multiple FlightInstances may reference the same Airline, Aircraft,
         * or Airport. Once data for an ID has been fetched through Feign,
         * it is stored in the corresponding map and reused for the remaining
         * FlightInstances in this batch.
         *
         * Example:
         *
         * Instance 101 → Airline 1, Aircraft 10, DEL → BOM
         * Instance 102 → Airline 1, Aircraft 10, DEL → BLR
         *
         * Airline 1 and Aircraft 10 are fetched only once.
         *
         * The Airport cache is shared between departure and arrival lookups,
         * so an Airport already fetched in either role is reused.
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
         * key   → FlightInstance database ID
         * value → Fully enriched FlightInstanceResponse
         */
        Map<Long, FlightInstanceResponse> result =
                new HashMap<>();

        for (FlightInstance fi : instances) {

            /*
             * Cache hit:
             * Return the AirlineResponse already fetched during this batch.
             *
             * Cache miss:
             * Call Airline Service, store the response, and return it.
             */
            AirlineResponse airline =
                    airlineCache.computeIfAbsent(
                            fi.getAirlineId(),
                            airlineClient::getAirlineById
                    );

            /*
             * Fetch each unique Aircraft only once during this batch request.
             */
            AircraftResponse aircraft =
                    aircraftCache.computeIfAbsent(
                            fi.getFlight().getAircraftId(),
                            airlineClient::getAircraftById
                    );

            /*
             * Reuse Airport responses across both departure and arrival
             * Airport lookups.
             */
            AirportResponse departure =
                    airportCache.computeIfAbsent(
                            fi.getDepartureAirportId(),
                            locationClient::getAirportById
                    );

            AirportResponse arrival =
                    airportCache.computeIfAbsent(
                            fi.getArrivalAirportId(),
                            locationClient::getAirportById
                    );

            result.put(
                    fi.getId(),
                    FlightInstanceMapper.toResponse(
                            fi,
                            aircraft,
                            airline,
                            departure,
                            arrival
                    )
            );
        }

        log.debug(
                "Batch flight instance lookup completed requestedCount={} returnedCount={} airlineLookups={} aircraftLookups={} airportLookups={}",
                ids.size(),
                result.size(),
                airlineCache.size(),
                aircraftCache.size(),
                airportCache.size()
        );

        return result;
    }


    // ==================== Cross-Service Helpers ====================

    /**
     * Fetches Aircraft information from Airline Core Service.
     *
     * Feign exceptions are translated into service-level exceptions so that
     * callers receive a meaningful failure instead of a raw Feign exception.
     */
    private AircraftResponse getAircraftById(
            Long aircraftId
    ) {

        log.debug(
                "Fetching aircraft from airline-core-service aircraftId={}",
                aircraftId
        );

        try {

            AircraftResponse aircraft =
                    airlineClient.getAircraftById(aircraftId);

            log.debug(
                    "Aircraft retrieved successfully aircraftId={}",
                    aircraftId
            );

            return aircraft;

        } catch (FeignException.NotFound exception) {

            log.warn(
                    "Aircraft lookup failed: aircraftId={} not found",
                    aircraftId
            );

            throw new AircraftNotFoundException(aircraftId);

        } catch (FeignException exception) {

            log.error(
                    "Aircraft lookup failed due to airline-core-service error aircraftId={} status={}",
                    aircraftId,
                    exception.status(),
                    exception
            );

            throw new RuntimeException(
                    "Failed to fetch aircraft from airline-core-service: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    /**
     * Resolves the Airline owned by the authenticated user.
     *
     * The FlightInstance stores the resolved Airline ID locally as a
     * cross-service reference for ownership filtering and airline queries.
     */
    private Long getAirlineForUser(
            Long userId
    ) {

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

            throw new AirlineNotFoundException(userId, true);

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


    // ==================== Response Enrichment Helper ====================

    /**
     * Builds a fully enriched FlightInstanceResponse.
     *
     * FlightInstance stores cross-service IDs locally. This helper resolves
     * the corresponding Airline, Aircraft, departure Airport, and arrival
     * Airport information before mapping the final API response.
     *
     * Enrichment Flow
     * ---------------
     * FlightInstance.airlineId
     *      → Airline Core Service
     *
     * FlightInstance.flight.aircraftId
     *      → Airline Core Service
     *
     * FlightInstance.departureAirportId
     *      → Location Service
     *
     * FlightInstance.arrivalAirportId
     *      → Location Service
     *
     * FlightInstance + enriched data
     *      → FlightInstanceResponse
     */
    private FlightInstanceResponse getFlightInstance(
            FlightInstance flightInstance
    )  {

        log.debug(
                "Enriching flight instance response flightInstanceId={} flightId={} airlineId={}",
                flightInstance.getId(),
                flightInstance.getFlight().getId(),
                flightInstance.getAirlineId()
        );

        AirlineResponse airline =
                airlineClient.getAirlineById(
                        flightInstance.getAirlineId()
                );

        AirportResponse departureAirport =
                locationClient.getAirportById(
                        flightInstance.getDepartureAirportId()
                );

        AirportResponse arrivalAirport =
                locationClient.getAirportById(
                        flightInstance.getArrivalAirportId()
                );

        AircraftResponse aircraftResponse =
                airlineClient.getAircraftById(
                        flightInstance.getFlight().getAircraftId()
                );

        log.debug(
                "Flight instance response enrichment completed flightInstanceId={} aircraftId={} departureAirportId={} arrivalAirportId={}",
                flightInstance.getId(),
                flightInstance.getFlight().getAircraftId(),
                flightInstance.getDepartureAirportId(),
                flightInstance.getArrivalAirportId()
        );

        return FlightInstanceMapper.toResponse(
                flightInstance,
                aircraftResponse,
                airline,
                departureAirport,
                arrivalAirport
        );
    }
}