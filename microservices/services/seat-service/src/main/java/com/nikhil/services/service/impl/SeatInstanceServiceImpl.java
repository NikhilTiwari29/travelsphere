package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.payload.request.SeatInstanceRequest;
import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import com.nikhil.services.mapper.SeatInstanceMapper;
import com.nikhil.services.model.FlightInstanceCabin;
import com.nikhil.services.model.Seat;
import com.nikhil.services.model.SeatInstance;
import com.nikhil.services.repository.FlightInstanceCabinRepository;
import com.nikhil.services.repository.SeatInstanceRepository;
import com.nikhil.services.repository.SeatRepository;
import com.nikhil.services.service.SeatInstanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/*
 * Manages runtime seat inventory for individual flight occurrences.
 *
 * Seat is the static aircraft-layout definition, while SeatInstance represents
 * that seat for a specific flight instance.
 *
 * Request Flow
 * ------------
 * SeatInstanceController / BookingEventListener
 *          ↓
 * SeatInstanceServiceImpl
 *          ↓
 * SeatInstanceRepository
 *
 * Main responsibilities:
 * - Create runtime SeatInstances.
 * - Retrieve flight-specific seat inventory.
 * - Retrieve currently available seats.
 * - Update runtime seat availability and booking status.
 * - Calculate seat-selection premium surcharges.
 *
 * Booking Service uses batch seat lookup and seat-price calculation during
 * the booking flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInstanceServiceImpl implements SeatInstanceService {

    private final SeatInstanceRepository seatInstanceRepository;
    private final SeatRepository seatRepository;
    private final FlightInstanceCabinRepository flightInstanceCabinRepository;


    // ==================== Create Operations ====================

    /**
     * Creates a runtime SeatInstance for a specific flight occurrence.
     *
     * Flow:
     * Seat lookup → FlightInstanceCabin lookup → Map request
     * → Save SeatInstance → Return response.
     *
     * The Seat provides the physical seat definition, while the
     * FlightInstanceCabin associates the runtime seat with a specific
     * cabin of a specific flight occurrence.
     */
    @Override
    @Transactional
    public SeatInstanceResponse createSeatInstance(
            SeatInstanceRequest request
    ) {

        log.info(
                "Creating seat instance flightId={} flightInstanceId={} cabinId={} seatId={}",
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getFlightInstanceCabinId(),
                request.getSeatId()
        );

        /*
         * Load the static Seat template that represents the physical
         * seat position in the aircraft layout.
         */
        Seat seat =
                seatRepository.findById(request.getSeatId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat instance creation failed: seat not found seatId={}",
                                    request.getSeatId()
                            );

                            return new EntityNotFoundException(
                                    "Seat not found with id: "
                                            + request.getSeatId()
                            );
                        });

        /*
         * Load the runtime cabin inventory to which this SeatInstance belongs.
         *
         * The FlightInstanceCabin groups all runtime seats belonging to one
         * cabin class of a specific flight instance.
         */
        FlightInstanceCabin fic = null;

        if (request.getFlightInstanceCabinId() != null) {

            fic = flightInstanceCabinRepository
                    .findById(request.getFlightInstanceCabinId())
                    .orElseThrow(() -> {

                        log.warn(
                                "Seat instance creation failed: flight instance cabin not found cabinId={}",
                                request.getFlightInstanceCabinId()
                        );

                        return new EntityNotFoundException(
                                "Flight instance cabin not found with id: "
                                        + request.getFlightInstanceCabinId()
                        );
                    });
        }

        SeatInstance seatInstance =
                SeatInstanceMapper.toEntity(
                        request,
                        seat,
                        fic
                );

        SeatInstance saved =
                seatInstanceRepository.save(seatInstance);

        log.info(
                "Seat instance created successfully seatInstanceId={} flightId={} flightInstanceId={} seatId={}",
                saved.getId(),
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getSeatId()
        );

        return SeatInstanceMapper.toResponse(saved);
    }


    // ==================== Read Operations ====================

    /**
     * Returns a runtime SeatInstance by its database identifier.
     */
    @Override
    @Transactional(readOnly = true)
    public SeatInstanceResponse getSeatInstanceById(Long id) {

        log.debug(
                "Fetching seat instance seatInstanceId={}",
                id
        );

        SeatInstance seatInstance =
                seatInstanceRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat instance lookup failed: seatInstanceId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat instance not found with id: " + id
                            );
                        });

        log.debug(
                "Seat instance retrieved successfully seatInstanceId={} flightId={} flightInstanceId={}",
                seatInstance.getId(),
                seatInstance.getFlightId(),
                seatInstance.getFlightInstanceId()
        );

        return SeatInstanceMapper.toResponse(seatInstance);
    }


    /**
     * Returns all runtime SeatInstances associated with the specified flight.
     *
     * The result can contain seats in different runtime states such as
     * AVAILABLE, HELD, or BOOKED.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeatInstanceResponse> getSeatInstancesByFlightId(
            Long flightId
    ) {

        log.debug(
                "Fetching seat instances flightId={}",
                flightId
        );

        List<SeatInstanceResponse> responses =
                seatInstanceRepository.findByFlightId(flightId)
                        .stream()
                        .map(SeatInstanceMapper::toResponse)
                        .collect(Collectors.toList());

        log.debug(
                "Seat instances retrieved flightId={} count={}",
                flightId,
                responses.size()
        );

        return responses;
    }


    /**
     * Returns only currently available SeatInstances for the specified flight.
     *
     * Used by the seat-selection flow to display seats that can currently
     * be selected by a passenger.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeatInstanceResponse> getAvailableSeatsByFlightId(
            Long flightId
    ) {

        log.debug(
                "Fetching available seat instances flightId={}",
                flightId
        );

        List<SeatInstanceResponse> responses =
                seatInstanceRepository.findAvailableByFlightId(flightId)
                        .stream()
                        .map(SeatInstanceMapper::toResponse)
                        .collect(Collectors.toList());

        log.debug(
                "Available seat instances retrieved flightId={} availableCount={}",
                flightId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves multiple SeatInstances in a single database operation.
     *
     * Booking Service uses this method to fetch details for all seats selected
     * in a booking without making one request per SeatInstance.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeatInstanceResponse> getAllByIds(
            List<Long> Ids
    ) {

        log.debug(
                "Batch seat instance lookup started requestedCount={}",
                Ids.size()
        );

        List<SeatInstance> seatInstances =
                seatInstanceRepository.findAllById(Ids);

        log.debug(
                "Batch seat instance lookup completed requestedCount={} foundCount={}",
                Ids.size(),
                seatInstances.size()
        );

        return seatInstances.stream()
                .map(SeatInstanceMapper::toResponse)
                .collect(Collectors.toList());
    }


    /**
     * Returns the number of currently available seats for the specified flight.
     *
     * This avoids loading full SeatInstance records when only the remaining
     * seat count is required.
     */
    @Override
    @Transactional(readOnly = true)
    public Long countAvailableByFlightId(Long flightId) {

        log.debug(
                "Counting available seats flightId={}",
                flightId
        );

        Long availableCount =
                seatInstanceRepository.countAvailableByFlightId(flightId);

        log.debug(
                "Available seat count retrieved flightId={} availableCount={}",
                flightId,
                availableCount
        );

        return availableCount;
    }


    // ==================== Status Operations ====================

    /**
     * Updates the runtime availability status of a SeatInstance.
     *
     * The SeatInstance is loaded using a pessimistic database lock so that
     * concurrent booking operations cannot update the same seat simultaneously.
     *
     * Current state synchronization:
     *
     * AVAILABLE:
     *   isAvailable = true
     *   isBooked = false
     *
     * BOOKED:
     *   isAvailable = false
     *   isBooked = true
     */
    @Override
    @Transactional
    public SeatInstanceResponse updateSeatInstanceStatus(
            Long id,
            SeatAvailabilityStatus status
    ) {

        log.info(
                "Updating seat instance status seatInstanceId={} targetStatus={}",
                id,
                status
        );

        /*
         * Load and lock the SeatInstance row for the duration of the transaction.
         *
         * This prevents concurrent booking transactions from modifying the
         * same seat state at the same time.
         */
        SeatInstance seatInstance =
                seatInstanceRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat status update failed: seatInstanceId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat instance not found with id: " + id
                            );
                        });

        SeatAvailabilityStatus previousStatus =
                seatInstance.getStatus();

        if (status == SeatAvailabilityStatus.AVAILABLE) {

            seatInstance.setAvailable(true);
            seatInstance.setBooked(false);

        } else if (status == SeatAvailabilityStatus.BOOKED) {

            seatInstance.setAvailable(false);
            seatInstance.setBooked(true);
        }

        seatInstance.setStatus(status);

        SeatInstance saved =
                seatInstanceRepository.save(seatInstance);

        log.info(
                "Seat instance status updated successfully seatInstanceId={} previousStatus={} newStatus={}",
                saved.getId(),
                previousStatus,
                status
        );

        return SeatInstanceMapper.toResponse(saved);
    }


    // ==================== Pricing Operations ====================

    /**
     * Calculates the total seat-selection premium for the requested
     * SeatInstances.
     *
     * Only the premium surcharge is summed here. The base flight fare,
     * taxes, and airline fees are calculated separately by the relevant
     * pricing and booking components.
     *
     * Example:
     *
     * WINDOW seat surcharge = 1000
     * AISLE seat surcharge  = 500
     *
     * Selected seats:
     * WINDOW + AISLE
     *
     * Total seat premium = 1500
     */
    @Override
    @Transactional(readOnly = true)
    public Double calculateSeatPrice(
            List<Long> seatInstanceIds
    ) {

        log.debug(
                "Calculating seat premium total requestedSeatCount={}",
                seatInstanceIds.size()
        );

        List<SeatInstance> seatInstances =
                seatInstanceRepository.findAllById(
                        seatInstanceIds
                );

        double total = 0.0;

        /*
         * Sum the premium surcharge attached to each selected SeatInstance.
         *
         * Seats without a configured premium surcharge contribute zero
         * to the final seat-selection charge.
         */
        for (SeatInstance seatInstance : seatInstances) {

            double seatPremium =
                    seatInstance.getPremiumSurcharge() != null
                            ? seatInstance.getPremiumSurcharge()
                            : 0.0;

            total += seatPremium;
        }

        log.debug(
                "Seat premium calculation completed requestedSeatCount={} foundSeatCount={} totalPremium={}",
                seatInstanceIds.size(),
                seatInstances.size(),
                total
        );

        return total;
    }
}