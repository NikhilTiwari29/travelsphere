package com.nikhil.services.service.impl;

import com.nikhil.common_lib.payload.request.SeatMapRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.common_lib.payload.response.SeatMapResponse;
import com.nikhil.services.clients.AirlineClient;
import com.nikhil.services.mapper.SeatMapMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.SeatMap;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.repository.SeatMapRepository;
import com.nikhil.services.service.SeatMapService;
import com.nikhil.services.service.SeatService;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for aircraft seat-map layout management.
 *
 * Manages seat-map creation, bulk creation, retrieval, update, and deletion.
 * A seat map belongs to a cabin class and defines the physical layout from
 * which Seat records are generated.
 *
 * Request flow:
 *
 * SeatMapController
 *      → SeatMapServiceImpl
 *          → AirlineClient
 *          → CabinClassRepository
 *          → SeatMapRepository
 *          → SeatService.generateSeats()
 *
 * Airline ownership is resolved through AirlineClient using the authenticated
 * user ID propagated from the Gateway.
 *
 * Domain hierarchy:
 *
 * Aircraft
 *      → CabinClass
 *          → SeatMap
 *              → Seat
 *                  → SeatInstance
 *
 * After a seat map is persisted, SeatService generates the corresponding
 * physical Seat records based on the configured layout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatMapServiceImpl implements SeatMapService {

    private final SeatMapRepository seatMapRepository;
    private final CabinClassRepository cabinClassRepository;
    private final AirlineClient airlineClient;
    private final SeatService seatService;


    // ==================== Create Operations ====================

    /**
     * Creates a seat map for a cabin class and generates its physical seats.
     *
     * Flow:
     * User → Airline → CabinClass → Aircraft ownership validation
     *      → SeatMap uniqueness check → Save SeatMap → Generate Seats.
     *
     * A cabin class belongs to a specific aircraft, and only one seat map
     * can exist for each cabin class.
     */
    @Override
    @Transactional
    public SeatMapResponse createSeatMap(
            Long userId,
            SeatMapRequest request
    ) throws Exception {

        log.info(
                "Creating seat map userId={} cabinClassId={} seatMapName={}",
                userId,
                request.getCabinClassId(),
                request.getName()
        );


        // Resolve the airline owned by the authenticated user.
        Long airlineId = getAirlineForUser(userId);

        log.debug(
                "Airline resolved for seat map creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Load the requested cabin class and derive its aircraft ID.
         * The aircraft ID is not trusted from the request because the
         * CabinClass → Aircraft relationship is the source of truth.
         */
        CabinClass cabinClass =
                cabinClassRepository.findById(request.getCabinClassId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat map creation failed: cabin class not found cabinClassId={}",
                                    request.getCabinClassId()
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: "
                                            + request.getCabinClassId()
                            );
                        });

        Long aircraftId = cabinClass.getAircraftId();

        /*
         * Verify that the aircraft associated with the cabin class
         * belongs to the authenticated user's airline.
         */
        AircraftResponse aircraft =
                airlineClient.getAircraftById(aircraftId);

        if (!aircraft.getAirlineId().equals(airlineId)) {

            log.warn(
                    "Seat map creation rejected: aircraft ownership mismatch userId={} airlineId={} aircraftId={} cabinClassId={}",
                    userId,
                    airlineId,
                    aircraftId,
                    request.getCabinClassId()
            );

            throw new IllegalArgumentException(
                    "Aircraft does not belong to the authenticated user's airline"
            );
        }

        /*
         * Enforce the one-to-one CabinClass → SeatMap relationship.
         */
        if (seatMapRepository.existsByCabinClassId(
                request.getCabinClassId()
        )) {

            log.warn(
                    "Seat map creation rejected: seat map already exists aircraftId={} cabinClassId={}",
                    aircraftId,
                    request.getCabinClassId()
            );

            throw new IllegalArgumentException(
                    "Seat map already exists for cabin class: "
                            + request.getCabinClassId()
            );
        }

        SeatMap seatMap =
                SeatMapMapper.toEntity(
                        request,
                        cabinClass
                );

        seatMap.setAircraftId(aircraftId);
        seatMap.setAirlineId(airlineId);

        SeatMap savedSeatMap =
                seatMapRepository.save(seatMap);

        log.info(
                "Seat map persisted successfully seatMapId={} airlineId={} aircraftId={} cabinClassId={}",
                savedSeatMap.getId(),
                airlineId,
                aircraftId,
                request.getCabinClassId()
        );

        /*
         * Generate physical Seat records from the persisted seat-map layout.
         */
        log.info(
                "Generating seats seatMapId={} aircraftId={} cabinClassId={}",
                savedSeatMap.getId(),
                aircraftId,
                request.getCabinClassId()
        );

        seatService.generateSeats(savedSeatMap.getId());

        log.info(
                "Seat generation completed successfully seatMapId={} aircraftId={} cabinClassId={}",
                savedSeatMap.getId(),
                aircraftId,
                request.getCabinClassId()
        );

        return SeatMapMapper.toResponse(savedSeatMap);
    }


    /**
     * Creates multiple seat maps for cabin classes belonging to aircraft
     * owned by the authenticated user's airline.
     *
     * Flow for each request:
     * CabinClass → Aircraft → Validate airline ownership
     * → Check SeatMap uniqueness → Build SeatMap.
     *
     * After all valid SeatMaps are persisted, physical Seat records are
     * generated for each newly created SeatMap.
     */
    @Override
    @Transactional
    public List<SeatMapResponse> createSeatMaps(
            Long userId,
            List<SeatMapRequest> requests
    ) throws Exception {

        log.info(
                "Bulk seat map creation started userId={} requestedCount={}",
                userId,
                requests.size()
        );

        /*
         * Resolve the airline owned by the authenticated user once
         * for the complete bulk operation.
         */
        Long airlineId = getAirlineForUser(userId);

        log.debug(
                "Airline resolved for bulk seat map creation userId={} airlineId={}",
                userId,
                airlineId
        );

        List<SeatMap> toSave = requests.stream()
                .filter(req -> {

                    /*
                     * A CabinClass can have only one SeatMap.
                     */
                    boolean exists =
                            seatMapRepository.existsByCabinClassId(
                                    req.getCabinClassId()
                            );

                    if (exists) {

                        log.warn(
                                "Skipping seat map creation: seat map already exists cabinClassId={} requestedName={}",
                                req.getCabinClassId(),
                                req.getName()
                        );
                    }

                    return !exists;
                })
                .map(req -> {

                    /*
                     * Load CabinClass and derive the aircraft ID from it.
                     *
                     * CabinClass → Aircraft is the source of truth.
                     */
                    CabinClass cabinClass =
                            cabinClassRepository.findById(
                                            req.getCabinClassId()
                                    )
                                    .orElseThrow(() -> {

                                        log.warn(
                                                "Bulk seat map creation failed: cabin class not found cabinClassId={}",
                                                req.getCabinClassId()
                                        );

                                        return new EntityNotFoundException(
                                                "Cabin class not found with id: "
                                                        + req.getCabinClassId()
                                        );
                                    });

                    Long aircraftId =
                            cabinClass.getAircraftId();

                    /*
                     * Fetch the aircraft and verify that it belongs to
                     * the authenticated user's airline.
                     */
                    AircraftResponse aircraft =
                            airlineClient.getAircraftById(
                                    aircraftId
                            );

                    if (!aircraft.getAirlineId().equals(airlineId)) {

                        log.warn(
                                "Bulk seat map creation rejected: aircraft ownership mismatch userId={} airlineId={} aircraftId={} cabinClassId={}",
                                userId,
                                airlineId,
                                aircraftId,
                                req.getCabinClassId()
                        );

                        throw new IllegalArgumentException(
                                "Aircraft "
                                        + aircraftId
                                        + " does not belong to the authenticated user's airline"
                        );
                    }

                    SeatMap seatMap =
                            SeatMapMapper.toEntity(
                                    req,
                                    cabinClass
                            );

                    /*
                     * Store explicit cross-service references for efficient
                     * ownership filtering and aircraft-level seat-map queries.
                     */
                    seatMap.setAircraftId(aircraftId);
                    seatMap.setAirlineId(airlineId);

                    log.debug(
                            "Seat map prepared for bulk creation seatMapName={} airlineId={} aircraftId={} cabinClassId={}",
                            req.getName(),
                            airlineId,
                            aircraftId,
                            req.getCabinClassId()
                    );

                    return seatMap;
                })
                .collect(Collectors.toList());

        List<SeatMap> saved =
                seatMapRepository.saveAll(toSave);

        log.info(
                "Seat maps persisted successfully airlineId={} requestedCount={} createdCount={} skippedCount={}",
                airlineId,
                requests.size(),
                saved.size(),
                requests.size() - saved.size()
        );

        /*
         * Generate physical Seat records for every newly persisted SeatMap.
         *
         * Because this method is transactional, SeatMap persistence and
         * Seat generation participate in the same transaction.
         */
        for (SeatMap seatMap : saved) {

            log.info(
                    "Generating seats seatMapId={} aircraftId={} cabinClassId={}",
                    seatMap.getId(),
                    seatMap.getAircraftId(),
                    seatMap.getCabinClass().getId()
            );

            seatService.generateSeats(
                    seatMap.getId()
            );

            log.info(
                    "Seat generation completed seatMapId={} aircraftId={} cabinClassId={}",
                    seatMap.getId(),
                    seatMap.getAircraftId(),
                    seatMap.getCabinClass().getId()
            );
        }

        log.info(
                "Bulk seat map creation completed airlineId={} createdCount={}",
                airlineId,
                saved.size()
        );

        return saved.stream()
                .map(SeatMapMapper::toResponse)
                .collect(Collectors.toList());
    }


    // ==================== Read Operations ====================

    /**
     * Returns a seat map by its unique database ID.
     *
     * The repository loads the seat map together with the details required
     * by the response mapper.
     *
     * Inherits the class-level read-only transaction.
     */
    @Override
    public SeatMapResponse getSeatMapById(Long id) {

        log.debug(
                "Fetching seat map seatMapId={}",
                id
        );

        SeatMap seatMap =
                seatMapRepository.findByIdWithDetails(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat map lookup failed: seatMapId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat map not found with id: " + id
                            );
                        });

        log.debug(
                "Seat map retrieved successfully seatMapId={}",
                id
        );

        return SeatMapMapper.toResponse(seatMap);
    }


    /**
     * Returns the seat map associated with the specified cabin class.
     *
     * The cabin class is validated first. The seat map is then resolved
     * using the cabin-class identifier.
     *
     * Inherits the class-level read-only transaction.
     */
    @Override
    public SeatMapResponse getSeatMapsByCabinClass(
            Long cabinClassId
    ) {

        log.debug(
                "Fetching seat map for cabinClassId={}",
                cabinClassId
        );

        cabinClassRepository.findById(cabinClassId)
                .orElseThrow(() -> {

                    log.warn(
                            "Seat map lookup failed: cabin class not found cabinClassId={}",
                            cabinClassId
                    );

                    return new EntityNotFoundException(
                            "CabinClass not found with id: "
                                    + cabinClassId
                    );
                });

        SeatMap seatMap =
                seatMapRepository.findByCabinClassId(cabinClassId);

        if (seatMap == null) {

            log.warn(
                    "Seat map not found for cabinClassId={}",
                    cabinClassId
            );

            throw new EntityNotFoundException(
                    "Seat map not found for cabin class id: "
                            + cabinClassId
            );
        }

        log.debug(
                "Seat map retrieved successfully cabinClassId={} seatMapId={}",
                cabinClassId,
                seatMap.getId()
        );

        return SeatMapMapper.toResponse(seatMap);
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing seat-map configuration.
     *
     * Flow:
     * User → Airline → Load SeatMap → Validate SeatMap ownership
     *      → CabinClass → Aircraft → Validate aircraft ownership
     *      → Validate CabinClass uniqueness → Update SeatMap.
     *
     * The CabinClass → Aircraft relationship is treated as the source of truth.
     * A cabin class can have only one seat map.
     */
    @Override
    @Transactional
    public SeatMapResponse updateSeatMap(
            Long userId,
            Long id,
            SeatMapRequest request
    ) {

        log.info(
                "Updating seat map seatMapId={} userId={} cabinClassId={} requestedName={}",
                id,
                userId,
                request.getCabinClassId(),
                request.getName()
        );

        /*
         * Resolve the airline owned by the authenticated user.
         */
        Long airlineId = getAirlineForUser(userId);

        log.debug(
                "Airline resolved for seat map update userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Load the existing SeatMap.
         */
        SeatMap existing =
                seatMapRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat map update failed: seat map not found seatMapId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat map not found with id: " + id
                            );
                        });

        /*
         * Prevent one airline from updating another airline's SeatMap.
         */
        if (!existing.getAirlineId().equals(airlineId)) {

            log.warn(
                    "Seat map update rejected: ownership mismatch seatMapId={} seatMapAirlineId={} authenticatedAirlineId={} userId={}",
                    id,
                    existing.getAirlineId(),
                    airlineId,
                    userId
            );

            throw new IllegalArgumentException(
                    "Seat map does not belong to the authenticated user's airline"
            );
        }

        /*
         * Load the requested CabinClass and derive the Aircraft ID from it.
         *
         * CabinClass → Aircraft is the source of truth. Aircraft ID is not
         * accepted independently from the request.
         */
        CabinClass cabinClass =
                cabinClassRepository.findById(request.getCabinClassId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat map update failed: cabin class not found seatMapId={} cabinClassId={}",
                                    id,
                                    request.getCabinClassId()
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: "
                                            + request.getCabinClassId()
                            );
                        });

        Long aircraftId = cabinClass.getAircraftId();

        /*
         * Verify that the aircraft associated with the requested CabinClass
         * belongs to the authenticated user's airline.
         */
        AircraftResponse aircraft =
                airlineClient.getAircraftById(aircraftId);

        if (!aircraft.getAirlineId().equals(airlineId)) {

            log.warn(
                    "Seat map update rejected: aircraft ownership mismatch userId={} airlineId={} aircraftId={} cabinClassId={} seatMapId={}",
                    userId,
                    airlineId,
                    aircraftId,
                    request.getCabinClassId(),
                    id
            );

            throw new IllegalArgumentException(
                    "Aircraft does not belong to the authenticated user's airline"
            );
        }

        /*
         * Enforce one SeatMap per CabinClass while excluding the current
         * SeatMap being updated.
         */
        if (seatMapRepository.existsByCabinClassIdAndIdNot(
                request.getCabinClassId(),
                id
        )) {

            log.warn(
                    "Seat map update rejected: another seat map already exists cabinClassId={} seatMapId={}",
                    request.getCabinClassId(),
                    id
            );

            throw new IllegalArgumentException(
                    "Another seat map already exists for cabin class: "
                            + request.getCabinClassId()
            );
        }

        /*
         * Update layout fields from the request.
         */
        SeatMapMapper.updateEntity(
                request,
                existing
        );

        /*
         * Synchronize the SeatMap ownership and hierarchy references with
         * the validated CabinClass and Aircraft.
         */
        existing.setCabinClass(cabinClass);
        existing.setAircraftId(aircraftId);
        existing.setAirlineId(airlineId);

        SeatMap saved =
                seatMapRepository.save(existing);

        log.info(
                "Seat map updated successfully seatMapId={} airlineId={} aircraftId={} cabinClassId={} seatMapName={}",
                saved.getId(),
                saved.getAirlineId(),
                saved.getAircraftId(),
                saved.getCabinClass().getId(),
                saved.getName()
        );

        return SeatMapMapper.toResponse(saved);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a seat map by its unique database ID.
     *
     * The method verifies existence before executing the delete operation.
     */
    @Override
    @Transactional
    public void deleteSeatMap(Long id) throws Exception {

        log.info(
                "Deleting seat map seatMapId={}",
                id
        );

        if (!seatMapRepository.existsById(id)) {

            log.warn(
                    "Seat map deletion failed: seatMapId={} not found",
                    id
            );

            throw new Exception(
                    "Seat map not found with id: " + id
            );
        }

        seatMapRepository.deleteById(id);

        log.info(
                "Seat map deleted successfully seatMapId={}",
                id
        );
    }


    // ==================== Airline Resolution ====================

    /**
     * Resolves the airline associated with the authenticated user.
     *
     * The lookup is performed through AirlineClient against the Airline Core
     * Service. A Feign 404 response is translated into EntityNotFoundException,
     * while other Feign failures are propagated as service communication errors.
     */
    private Long getAirlineForUser(Long userId) {

        log.debug(
                "Resolving airline for userId={} through AirlineClient",
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
                    "Airline resolution failed: no airline found for userId={}",
                    userId
            );

            throw new EntityNotFoundException(
                    "No airline found for user: " + userId
            );

        } catch (FeignException exception) {

            log.error(
                    "Airline Core Service communication failed userId={} status={} message={}",
                    userId,
                    exception.status(),
                    exception.getMessage()
            );

            throw new RuntimeException(
                    "Failed to fetch airline from airline-core-service: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}