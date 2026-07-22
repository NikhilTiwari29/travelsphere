package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.BadRequestException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AircraftRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.services.exception.AircraftAlreadyExistsException;
import com.nikhil.services.exception.AircraftNotFoundException;
import com.nikhil.services.exception.AircraftOwnershipMismatchException;
import com.nikhil.services.exception.AirlineNotFoundException;
import com.nikhil.services.mapper.AircraftMapper;
import com.nikhil.services.model.Aircraft;
import com.nikhil.services.model.Airline;
import com.nikhil.services.repository.AircraftRepository;
import com.nikhil.services.repository.AirlineRepository;
import com.nikhil.services.service.AircraftService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service implementation for aircraft fleet management.
 *
 * Aircraft operations are scoped to the airline associated with the
 * authenticated owner. The service manages aircraft creation, retrieval,
 * updates, deletion, and domain-level aircraft data validation.
 *
 * Individual aircraft lookups are cached in Redis by aircraft ID.
 * Write operations evict the corresponding cache entry to prevent stale reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AircraftServiceImpl implements AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AirlineRepository airlineRepository;


    // ==================== Create Operations ====================

    /**
     * Creates a new aircraft for the airline owned by the authenticated user.
     *
     * Validates aircraft code uniqueness and aircraft capacity constraints
     * before persistence.
     */
    @Override
    @Transactional
    public AircraftResponse createAircraft(
            AircraftRequest request,
            Long ownerId
    ) throws ResourceNotFoundException {

        log.info(
                "Creating aircraft for ownerId={} code={}",
                ownerId,
                request.getCode()
        );

        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft creation failed: airline not found for ownerId={}",
                            ownerId
                    );

                    return new AirlineNotFoundException(ownerId);
                });

        Aircraft aircraft =
                AircraftMapper.toEntity(request, airline);

        /*
         * Aircraft code is globally unique across the fleet registry.
         * Reject duplicates before attempting persistence.
         */
        if (aircraftRepository.existsByCode(aircraft.getCode())) {

            log.warn(
                    "Aircraft creation rejected: duplicate code={}",
                    aircraft.getCode()
            );

            throw new AircraftAlreadyExistsException(
                    aircraft.getCode()
            );
        }

        validateAircraftData(aircraft);

        Aircraft saved =
                aircraftRepository.save(aircraft);

        log.info(
                "Aircraft created successfully aircraftId={} code={} airlineId={}",
                saved.getId(),
                saved.getCode(),
                airline.getId()
        );

        return AircraftMapper.toResponse(saved);
    }


    // ==================== Read Operations ====================

    /**
     * Returns an aircraft by its unique database ID.
     *
     * Results are cached by aircraft ID to reduce repeated database reads
     * from internal consumers such as flight, seat, and ancillary services.
     */
    @Override
    @Cacheable(
            cacheNames = "aircrafts",
            key = "#id"
    )
    public AircraftResponse getAircraftById(Long id)
            throws ResourceNotFoundException {

        log.debug(
                "Fetching aircraft by aircraftId={}",
                id
        );

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft not found aircraftId={}",
                            id
                    );

                    return new AircraftNotFoundException(id);
                });

        return AircraftMapper.toResponse(aircraft);
    }


    /**
     * Returns all aircraft belonging to the airline associated with
     * the authenticated owner.
     */
    @Override
    public List<AircraftResponse> listAllAircraftsByOwner(
            Long ownerId
    ) {

        log.debug(
                "Fetching aircraft fleet for ownerId={}",
                ownerId
        );

        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft fleet lookup failed: airline not found for ownerId={}",
                            ownerId
                    );

                    return new AirlineNotFoundException(ownerId);
                });

        List<AircraftResponse> aircrafts =
                aircraftRepository.findByAirline(airline)
                        .stream()
                        .map(AircraftMapper::toResponse)
                        .toList();

        log.debug(
                "Aircraft fleet retrieved ownerId={} airlineId={} count={}",
                ownerId,
                airline.getId(),
                aircrafts.size()
        );

        return aircrafts;
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing aircraft belonging to the authenticated
     * airline owner.
     *
     * The cached aircraft entry is evicted after successful method execution
     * so subsequent reads retrieve the latest database state.
     */
    @Override
    @Transactional
    @CacheEvict(
            cacheNames = "aircrafts",
            key = "#id"
    )
    public AircraftResponse updateAircraft(
            Long id,
            AircraftRequest request,
            Long ownerId
    ) throws ResourceNotFoundException {

        log.info(
                "Updating aircraft aircraftId={} ownerId={}",
                id,
                ownerId
        );

        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft update failed: airline not found for ownerId={}",
                            ownerId
                    );

                    return new AirlineNotFoundException(ownerId);
                });

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft update failed: aircraft not found aircraftId={}",
                            id
                    );

                    return new AircraftNotFoundException(id);
                });

        /*
         * Prevent one airline owner from modifying an aircraft
         * belonging to another airline.
         */
        if (!aircraft.getAirline().getId().equals(airline.getId())) {

            log.warn(
                    "Unauthorized aircraft update attempt aircraftId={} ownerId={} airlineId={}",
                    id,
                    ownerId,
                    airline.getId()
            );

            throw new AircraftOwnershipMismatchException();
        }

        String oldCode = aircraft.getCode();
        String newCode = request.getCode();

        /*
         * Check code uniqueness before modifying the managed entity.
         *
         * Updating the managed entity first may cause Hibernate to auto-flush
         * the changed aircraft code before executing the existsByCode query,
         * resulting in a database unique-constraint violation.
         */
        if (!oldCode.equals(newCode)
                && aircraftRepository.existsByCode(newCode)) {

            log.warn(
                    "Aircraft update rejected: duplicate code={} aircraftId={}",
                    newCode,
                    id
            );

            throw new AircraftAlreadyExistsException(newCode);
        }

        // Apply changes only after ownership and uniqueness validation succeeds.
        AircraftMapper.updateEntity(
                aircraft,
                request,
                airline
        );

        validateAircraftData(aircraft);

        Aircraft saved =
                aircraftRepository.save(aircraft);

        log.info(
                "Aircraft updated successfully aircraftId={} code={}",
                saved.getId(),
                saved.getCode()
        );

        return AircraftMapper.toResponse(saved);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes an aircraft by its unique ID.
     *
     * The corresponding Redis cache entry is evicted after successful
     * method execution.
     *
     * Ownership validation should be added when ownerId is propagated
     * through the controller and service interface.
     */
    @Override
    @Transactional
    @CacheEvict(
            cacheNames = "aircrafts",
            key = "#id"
    )
    public void deleteAircraft(Long id)
            throws ResourceNotFoundException {

        log.info(
                "Deleting aircraft aircraftId={}",
                id
        );

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn(
                            "Aircraft deletion failed: aircraft not found aircraftId={}",
                            id
                    );

                    return new AircraftNotFoundException(id);
                });

        aircraftRepository.delete(aircraft);

        log.info(
                "Aircraft deleted successfully aircraftId={} code={}",
                id,
                aircraft.getCode()
        );
    }


    // ==================== Validation ====================

    /**
     * Validates aircraft domain constraints before persistence.
     *
     * Ensures:
     * - seating capacity is positive,
     * - configured cabin seats do not exceed total aircraft capacity,
     * - manufacture year falls within a valid historical range.
     */
    private void validateAircraftData(Aircraft aircraft) {

        if (aircraft.getSeatingCapacity() != null
                && aircraft.getSeatingCapacity() <= 0) {

            log.warn(
                    "Aircraft validation failed: invalid seating capacity code={} capacity={}",
                    aircraft.getCode(),
                    aircraft.getSeatingCapacity()
            );

            throw new BadRequestException(
                    ErrorCode.INVALID_SEATING_CAPACITY,
                    "Seating capacity must be greater than zero."
            );
        }

        int totalSpecifiedSeats =
                (aircraft.getEconomySeats() != null
                        ? aircraft.getEconomySeats()
                        : 0)
                        +
                        (aircraft.getPremiumEconomySeats() != null
                                ? aircraft.getPremiumEconomySeats()
                                : 0)
                        +
                        (aircraft.getBusinessSeats() != null
                                ? aircraft.getBusinessSeats()
                                : 0)
                        +
                        (aircraft.getFirstClassSeats() != null
                                ? aircraft.getFirstClassSeats()
                                : 0);

        if (aircraft.getSeatingCapacity() != null
                && totalSpecifiedSeats > aircraft.getSeatingCapacity()) {

            log.warn(
                    "Aircraft validation failed: seat breakdown exceeds capacity code={} specifiedSeats={} capacity={}",
                    aircraft.getCode(),
                    totalSpecifiedSeats,
                    aircraft.getSeatingCapacity()
            );

            throw new BadRequestException(
                    ErrorCode.INVALID_AIRCRAFT_CONFIGURATION,
                    "Total specified seats exceed aircraft seating capacity."
            );
        }

        if (aircraft.getYearOfManufacture() != null
                && (aircraft.getYearOfManufacture() < 1900
                || aircraft.getYearOfManufacture()
                > LocalDate.now().getYear())) {

            log.warn(
                    "Aircraft validation failed: invalid manufacture year code={} year={}",
                    aircraft.getCode(),
                    aircraft.getYearOfManufacture()
            );

            throw new BadRequestException(
                    ErrorCode.INVALID_MANUFACTURE_YEAR,
                    "Invalid year of manufacture."
            );
        }
    }
}