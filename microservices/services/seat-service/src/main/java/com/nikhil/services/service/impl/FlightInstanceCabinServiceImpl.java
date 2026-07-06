package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.enums.SeatType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightInstanceCabinRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceCabinResponse;
import com.nikhil.services.mapper.FlightInstanceCabinMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.FlightInstanceCabin;
import com.nikhil.services.model.SeatInstance;
import com.nikhil.services.model.SeatMap;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.repository.FlightInstanceCabinRepository;
import com.nikhil.services.repository.SeatInstanceRepository;
import com.nikhil.services.repository.SeatMapRepository;
import com.nikhil.services.repository.SeatRepository;
import com.nikhil.services.service.FlightInstanceCabinService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation for provisioning and managing cabin inventory
 * for individual flight instances.
 *
 * A FlightInstanceCabin represents one cabin class, such as ECONOMY,
 * BUSINESS, or FIRST, for a specific flight occurrence.
 *
 * Provisioning flow:
 *
 * Flight Instance
 *      → CabinClass
 *          → SeatMap
 *              → Template Seats
 *                  → SeatInstances for the flight occurrence
 *
 * Seat records represent the permanent physical aircraft layout, while
 * SeatInstance records represent the runtime availability and booking state
 * of those seats for a specific flight instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightInstanceCabinServiceImpl
        implements FlightInstanceCabinService {

    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final CabinClassRepository cabinClassRepository;
    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatInstanceRepository seatInstanceRepository;


    // ==================== Create Operations ====================

    /**
     * Creates a cabin inventory bucket for a flight instance and provisions
     * runtime SeatInstance records from the cabin's template SeatMap.
     *
     * Flow:
     * CabinClass → SeatMap → Validate template Seats
     * → Create FlightInstanceCabin → Clone Seats into SeatInstances.
     *
     * Every generated SeatInstance initially starts as AVAILABLE and unbooked.
     */
    @Override
    @Transactional
    public FlightInstanceCabinResponse createFlightInstanceCabin(
            FlightInstanceCabinRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating flight instance cabin flightId={} flightInstanceId={} cabinClassId={}",
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getCabinClassId()
        );

        /*
         * Load the CabinClass whose physical SeatMap will be used
         * as the template for runtime seat provisioning.
         */
        CabinClass cabinClass =
                cabinClassRepository.findById(
                                request.getCabinClassId()
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance cabin creation failed: cabin class not found cabinClassId={} flightInstanceId={}",
                                    request.getCabinClassId(),
                                    request.getFlightInstanceId()
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: "
                                            + request.getCabinClassId()
                            );
                        });

        /*
         * Resolve the SeatMap associated with the CabinClass.
         *
         * The SeatMap contains the static physical Seat records that
         * will be cloned into runtime SeatInstance records.
         */
        SeatMap seatMap =
                seatMapRepository.findByCabinClassId(
                        cabinClass.getId()
                );

        if (seatMap == null) {

            log.warn(
                    "Flight instance cabin creation failed: seat map not found cabinClassId={} flightInstanceId={}",
                    cabinClass.getId(),
                    request.getFlightInstanceId()
            );

            throw new EntityNotFoundException(
                    "Seat map not found for cabin class id: "
                            + cabinClass.getId()
            );
        }

        /*
         * A SeatMap without generated Seat records cannot be used
         * for flight-instance seat provisioning.
         */
        if (seatMap.getSeats() == null
                || seatMap.getSeats().isEmpty()) {

            log.warn(
                    "Flight instance cabin creation failed: no template seats found seatMapId={} cabinClassId={} flightInstanceId={}",
                    seatMap.getId(),
                    cabinClass.getId(),
                    request.getFlightInstanceId()
            );

            throw new ResourceNotFoundException(
                    "No seats found in the seat map for cabin class id "
                            + cabinClass.getId()
            );
        }

        int totalSeats =
                seatMap.getSeats().size();

        log.debug(
                "Seat map resolved for cabin provisioning seatMapId={} cabinClassId={} totalSeats={}",
                seatMap.getId(),
                cabinClass.getId(),
                totalSeats
        );

        /*
         * Create the cabin inventory bucket for the flight instance.
         *
         * bookedSeats starts at zero because no SeatInstance has been
         * reserved or booked at provisioning time.
         */
        FlightInstanceCabin flightInstanceCabin =
                FlightInstanceCabin.builder()
                        .flightInstanceId(
                                request.getFlightInstanceId()
                        )
                        .cabinClass(cabinClass)
                        .totalSeats(totalSeats)
                        .bookedSeats(0)
                        .build();

        FlightInstanceCabin savedFlightInstanceCabin =
                flightInstanceCabinRepository.save(
                        flightInstanceCabin
                );

        log.info(
                "Flight instance cabin persisted cabinId={} flightInstanceId={} cabinClassId={} totalSeats={}",
                savedFlightInstanceCabin.getId(),
                request.getFlightInstanceId(),
                cabinClass.getId(),
                totalSeats
        );

        /*
         * Create runtime seat inventory for this specific flight occurrence.
         *
         * Seat records from the SeatMap are static aircraft-layout templates
         * (for example, 12A WINDOW or 12B MIDDLE). For each template Seat,
         * create a SeatInstance that tracks its state for this particular
         * flight instance.
         *
         * Flow:
         * SeatMap → Seat templates → SeatInstances for the FlightInstanceCabin.
         *
         * Every new SeatInstance starts as AVAILABLE and unbooked. A seat-level
         * surcharge is calculated from the physical seat type, such as WINDOW
         * or AISLE, before the runtime inventory is persisted.
         */
        log.info(
                "Generating seat instances cabinId={} flightId={} flightInstanceId={} seatCount={}",
                savedFlightInstanceCabin.getId(),
                request.getFlightId(),
                request.getFlightInstanceId(),
                totalSeats
        );

        List<SeatInstance> seatInstances =
                seatMap.getSeats()
                        .stream()
                        .map(seat -> {

                            /*
                             * Calculate the additional seat-selection charge based
                             * on the physical seat type defined in the Seat template.
                             */
                            Double premiumSurcharge =
                                    getPremiumSurcharge(
                                            seat.getSeatType(),
                                            1000.0,
                                            500.0
                                    );

                            /*
                             * Create the runtime representation of this physical seat
                             * for the requested flight instance and cabin inventory.
                             */
                            return SeatInstance.builder()
                                    .flightId(
                                            request.getFlightId()
                                    )
                                    .status(
                                            SeatAvailabilityStatus.AVAILABLE
                                    )
                                    .flightInstanceId(
                                            request.getFlightInstanceId()
                                    )
                                    .flightInstanceCabin(
                                            savedFlightInstanceCabin
                                    )
                                    .seat(seat)
                                    .isAvailable(true)
                                    .isBooked(false)
                                    .premiumSurcharge(
                                            premiumSurcharge
                                    )
                                    .build();
                        })
                        .toList();

        /*
         * Persist all generated runtime seat inventory records in a single
         * repository operation.
         */
        seatInstanceRepository.saveAll(
                seatInstances
        );

        /*
         * Synchronize the in-memory relationship before response mapping.
         */
        savedFlightInstanceCabin.setSeats(
                seatInstances
        );

        log.info(
                "Flight instance cabin created successfully cabinId={} flightId={} flightInstanceId={} cabinClassId={} generatedSeatInstances={}",
                savedFlightInstanceCabin.getId(),
                request.getFlightId(),
                request.getFlightInstanceId(),
                cabinClass.getId(),
                seatInstances.size()
        );

        return FlightInstanceCabinMapper.toResponse(
                savedFlightInstanceCabin
        );
    }


    // ==================== Read Operations ====================

    /**
     * Returns a flight-instance cabin by its unique database ID.
     */
    @Override
    public FlightInstanceCabinResponse getFlightInstanceCabinById(
            Long id
    ) {

        log.debug(
                "Fetching flight instance cabin cabinId={}",
                id
        );

        FlightInstanceCabin flightInstanceCabin =
                flightInstanceCabinRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance cabin lookup failed: cabin not found cabinId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight instance cabin not found with id: "
                                            + id
                            );
                        });

        log.debug(
                "Flight instance cabin retrieved successfully cabinId={} flightInstanceId={}",
                flightInstanceCabin.getId(),
                flightInstanceCabin.getFlightInstanceId()
        );

        return FlightInstanceCabinMapper.toResponse(
                flightInstanceCabin
        );
    }


    /**
     * Returns paginated cabin inventory records belonging to a flight instance.
     */
    @Override
    public Page<FlightInstanceCabinResponse> getByFlightInstanceId(
            Long flightInstanceId,
            Pageable pageable
    ) {

        log.debug(
                "Fetching flight instance cabins flightInstanceId={} page={} size={}",
                flightInstanceId,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<FlightInstanceCabinResponse> responses =
                flightInstanceCabinRepository
                        .findByFlightInstanceId(
                                flightInstanceId,
                                pageable
                        )
                        .map(
                                FlightInstanceCabinMapper::toResponse
                        );

        log.debug(
                "Flight instance cabins retrieved flightInstanceId={} resultCount={} totalElements={}",
                flightInstanceId,
                responses.getNumberOfElements(),
                responses.getTotalElements()
        );

        return responses;
    }


    /**
     * Returns the cabin inventory record for a specific flight instance
     * and cabin class combination.
     */
    @Override
    public FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(
            Long flightInstanceId,
            Long cabinClassId
    ) {

        log.debug(
                "Fetching flight instance cabin flightInstanceId={} cabinClassId={}",
                flightInstanceId,
                cabinClassId
        );

        FlightInstanceCabin cabin =
                flightInstanceCabinRepository
                        .findByFlightInstanceIdAndCabinClassId(
                                flightInstanceId,
                                cabinClassId
                        );

        log.debug(
                "Flight instance cabin lookup completed flightInstanceId={} cabinClassId={}",
                flightInstanceId,
                cabinClassId
        );

        return FlightInstanceCabinMapper.toResponse(
                cabin
        );
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing flight-instance cabin.
     *
     * The record is loaded using a repository-level locking query to protect
     * concurrent modifications to cabin inventory state.
     *
     * If cabinClassId is provided, the cabin association is updated after
     * validating that the requested CabinClass exists.
     */
    @Override
    @Transactional
    public FlightInstanceCabinResponse updateFlightInstanceCabin(
            Long id,
            FlightInstanceCabinRequest request
    ) {

        log.info(
                "Updating flight instance cabin cabinId={} requestedCabinClassId={}",
                id,
                request.getCabinClassId()
        );

        /*
         * Load the cabin using the repository's locking query to prevent
         * concurrent updates from modifying the same inventory record.
         */
        FlightInstanceCabin existing =
                flightInstanceCabinRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance cabin update failed: cabin not found cabinId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight instance cabin not found with id: "
                                            + id
                            );
                        });

        /*
         * Update the CabinClass association only when a new cabinClassId
         * is provided in the request.
         */
        if (request.getCabinClassId() != null) {

            CabinClass cabinClass =
                    cabinClassRepository.findById(
                                    request.getCabinClassId()
                            )
                            .orElseThrow(() -> {

                                log.warn(
                                        "Flight instance cabin update failed: cabin class not found cabinId={} cabinClassId={}",
                                        id,
                                        request.getCabinClassId()
                                );

                                return new EntityNotFoundException(
                                        "Cabin class not found with id: "
                                                + request.getCabinClassId()
                                );
                            });

            existing.setCabinClass(
                    cabinClass
            );
        }

        FlightInstanceCabin saved =
                flightInstanceCabinRepository.save(
                        existing
                );

        log.info(
                "Flight instance cabin updated successfully cabinId={} flightInstanceId={} cabinClassId={}",
                saved.getId(),
                saved.getFlightInstanceId(),
                saved.getCabinClass() != null
                        ? saved.getCabinClass().getId()
                        : null
        );

        return FlightInstanceCabinMapper.toResponse(
                saved
        );
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a flight-instance cabin by ID.
     *
     * Related SeatInstance deletion behavior depends on the entity relationship
     * and cascade configuration defined on FlightInstanceCabin.
     */
    @Override
    @Transactional
    public void deleteFlightInstanceCabin(
            Long id
    ) {

        log.info(
                "Deleting flight instance cabin cabinId={}",
                id
        );

        FlightInstanceCabin flightInstanceCabin =
                flightInstanceCabinRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight instance cabin deletion failed: cabin not found cabinId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight instance cabin not found with id: "
                                            + id
                            );
                        });

        flightInstanceCabinRepository.delete(
                flightInstanceCabin
        );

        log.info(
                "Flight instance cabin deleted successfully cabinId={} flightInstanceId={}",
                id,
                flightInstanceCabin.getFlightInstanceId()
        );
    }


    // ==================== Pricing Helpers ====================

    /**
     * Calculates the seat-position surcharge used when creating SeatInstances.
     *
     * WINDOW seats receive the configured window surcharge.
     * AISLE seats receive the configured aisle surcharge.
     * All other seat types receive no positional surcharge.
     */
    private Double getPremiumSurcharge(
            SeatType seatType,
            Double windowSurcharge,
            Double aisleSurcharge
    ) {

        if (seatType == null) {
            return 0.0;
        }

        return switch (seatType) {

            case WINDOW ->
                    windowSurcharge != null
                            ? windowSurcharge
                            : 0.0;

            case AISLE ->
                    aisleSurcharge != null
                            ? aisleSurcharge
                            : 0.0;

            default -> 0.0;
        };
    }
}