package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import com.nikhil.services.Integration.AirlineIntegrationService;
import com.nikhil.services.client.LocationClient;
import com.nikhil.services.mapper.FlightScheduleMapper;
import com.nikhil.services.model.Flight;
import com.nikhil.services.model.FlightSchedule;
import com.nikhil.services.repository.FlightRepository;
import com.nikhil.services.repository.FlightScheduleRepository;
import com.nikhil.services.service.FlightInstanceService;
import com.nikhil.services.service.FlightScheduleService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Manages recurring FlightSchedule definitions and expands schedules into
 * concrete dated FlightInstance records.
 *
 * Creation Flow
 * -------------
 * Validate Flight
 *      ↓
 * Validate schedule date range
 *      ↓
 * Save FlightSchedule
 *      ↓
 * Fetch Aircraft capacity
 *      ↓
 * Iterate through schedule date range
 *      ↓
 * Create FlightInstance for every matching operating date
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightScheduleServiceImpl implements FlightScheduleService {

    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightRepository flightRepository;
    private final FlightInstanceService flightInstanceService;
    private final AirlineIntegrationService airlineIntegrationService;
    private final LocationClient locationClient;


    // ==================== Create Operations ====================

    /**
     * Creates a recurring FlightSchedule and expands it into concrete
     * FlightInstances for every matching operating date.
     *
     * The complete database operation runs within one transaction:
     *
     * FlightSchedule
     *      ↓
     * FlightInstances
     *      ↓
     * Related runtime records created by FlightInstanceService
     *
     * If a runtime exception occurs during database provisioning,
     * the transaction is rolled back.
     */
    @Override
    @Transactional
    public FlightScheduleResponse createFlightSchedule(
            Long userId,
            FlightScheduleRequest request
    ) throws Exception {

        log.info(
                "Creating flight schedule userId={} flightId={} startDate={} endDate={}",
                userId,
                request.getFlightId(),
                request.getStartDate(),
                request.getEndDate()
        );

        Flight flight =
                flightRepository.findById(request.getFlightId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight schedule creation failed: flight not found flightId={}",
                                    request.getFlightId()
                            );

                            return new EntityNotFoundException(
                                    "Flight not found with id: "
                                            + request.getFlightId()
                            );
                        });

        log.debug(
                "Flight loaded for schedule creation flightId={} flightNumber={} aircraftId={}",
                flight.getId(),
                flight.getFlightNumber(),
                flight.getAircraftId()
        );

        /*
         * Reject an invalid schedule period before creating any persistent
         * schedule or FlightInstance records.
         */
        if (request.getEndDate().isBefore(request.getStartDate())) {

            log.warn(
                    "Flight schedule creation rejected: invalid date range flightId={} startDate={} endDate={}",
                    request.getFlightId(),
                    request.getStartDate(),
                    request.getEndDate()
            );

            throw new IllegalArgumentException(
                    "End date must be after start date"
            );
        }

        FlightSchedule schedule =
                FlightScheduleMapper.toEntity(
                        request,
                        flight
                );

        FlightSchedule savedSchedule =
                flightScheduleRepository.save(schedule);

        log.info(
                "Flight schedule persisted scheduleId={} flightId={} startDate={} endDate={}",
                savedSchedule.getId(),
                flight.getId(),
                savedSchedule.getStartDate(),
                savedSchedule.getEndDate()
        );

        /*
         * Fetch aircraft capacity used while creating each dated
         * FlightInstance generated from this schedule.
         */
        AircraftResponse aircraft =
                airlineIntegrationService.getAircraftById(
                        flight.getAircraftId()
                );

        List<DayOfWeek> operatingDays =
                savedSchedule.getOperatingDays();

        LocalDate startDate =
                savedSchedule.getStartDate();

        LocalDate endDate =
                savedSchedule.getEndDate();

        /*
         * Build the fields common to every FlightInstance generated from
         * this schedule. Only departure and arrival date-time values change
         * for each operating date.
         */
        FlightInstanceRequest flightInstanceRequest =
                FlightInstanceRequest.builder()
                        .scheduleId(savedSchedule.getId())
                        .flightId(flight.getId())
                        .arrivalAirportId(
                                flight.getArrivalAirportId()
                        )
                        .departureAirportId(
                                flight.getDepartureAirportId()
                        )
                        .totalSeats(
                                aircraft.getTotalSeats()
                        )
                        .status(
                                FlightStatus.SCHEDULED
                        )
                        .build();

        /*
         * Expand the recurring schedule into actual dated FlightInstances.
         *
         * Walk through every date in the schedule period. A concrete
         * FlightInstance is created only when the date's weekday is included
         * in the configured operating days.
         *
         * Example:
         *
         * Date range:
         *   2026-07-01 → 2026-07-31
         *
         * Operating days:
         *   MONDAY, WEDNESDAY, FRIDAY
         *
         * Each matching Monday, Wednesday, and Friday becomes a separate
         * FlightInstance representing one actual flight occurrence.
         */
        int generatedInstanceCount = 0;

        for (
                LocalDate date = startDate;
                !date.isAfter(endDate);
                date = date.plusDays(1)
        ) {

            if (operatingDays.contains(date.getDayOfWeek())) {

                /*
                 * Convert the schedule's recurring local times into exact
                 * date-time values for this particular flight occurrence.
                 */
                flightInstanceRequest.setDepartureDateTime(
                        LocalDateTime.of(
                                date,
                                savedSchedule.getDepartureTime()
                        )
                );

                flightInstanceRequest.setArrivalDateTime(
                        LocalDateTime.of(
                                date,
                                savedSchedule.getArrivalTime()
                        )
                );

                log.debug(
                        "Creating flight instance scheduleId={} flightId={} operatingDate={} departureDateTime={} arrivalDateTime={}",
                        savedSchedule.getId(),
                        flight.getId(),
                        date,
                        flightInstanceRequest.getDepartureDateTime(),
                        flightInstanceRequest.getArrivalDateTime()
                );

                /*
                 * Create the concrete flight occurrence and delegate its
                 * runtime provisioning to FlightInstanceService.
                 *
                 * With default REQUIRED transaction propagation, the called
                 * service participates in the transaction started by this
                 * createFlightSchedule method.
                 */
                flightInstanceService.createFlightInstanceWithCabins(
                        userId,
                        flightInstanceRequest
                );

                generatedInstanceCount++;

                log.debug(
                        "Flight instance created scheduleId={} flightId={} operatingDate={}",
                        savedSchedule.getId(),
                        flight.getId(),
                        date
                );
            }
        }

        log.info(
                "Flight schedule creation completed scheduleId={} flightId={} generatedInstanceCount={}",
                savedSchedule.getId(),
                flight.getId(),
                generatedInstanceCount
        );

        return getFlightScheduleResponse(savedSchedule);
    }


    // ==================== Read Operations ====================

    @Override
    @Transactional(readOnly = true)
    public FlightScheduleResponse getFlightScheduleById(
            Long id
    ) throws AirportException {

        log.debug(
                "Fetching flight schedule scheduleId={}",
                id
        );

        FlightSchedule schedule =
                flightScheduleRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight schedule lookup failed: scheduleId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight schedule not found with id: " + id
                            );
                        });

        return getFlightScheduleResponse(schedule);
    }


    @Override
    @Transactional(readOnly = true)
    public List<FlightScheduleResponse> getFlightScheduleByAirline(
            Long userId
    ) {

        log.debug(
                "Fetching flight schedules for user userId={}",
                userId
        );

        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        log.debug(
                "Airline resolved for schedule lookup userId={} airlineId={}",
                userId,
                airlineId
        );

        List<FlightSchedule> schedules =
                flightScheduleRepository.findByFlightAirlineId(
                        airlineId
                );

        log.debug(
                "Flight schedules loaded airlineId={} scheduleCount={}",
                airlineId,
                schedules.size()
        );

        List<FlightScheduleResponse> responses =
                schedules.stream()
                        .map(schedule -> {
                            try {

                                return getFlightScheduleResponse(schedule);

                            } catch (AirportException exception) {

                                log.error(
                                        "Failed to enrich flight schedule scheduleId={} airlineId={}",
                                        schedule.getId(),
                                        airlineId,
                                        exception
                                );

                                throw new RuntimeException(exception);
                            }
                        })
                        .collect(Collectors.toList());

        log.debug(
                "Flight schedule lookup completed airlineId={} returnedCount={}",
                airlineId,
                responses.size()
        );

        return responses;
    }


    // ==================== Update Operations ====================

    @Override
    @Transactional
    public FlightScheduleResponse updateFlightSchedule(
            Long id,
            FlightScheduleRequest request
    ) throws AirportException {

        log.info(
                "Updating flight schedule scheduleId={} flightId={} startDate={} endDate={}",
                id,
                request.getFlightId(),
                request.getStartDate(),
                request.getEndDate()
        );

        FlightSchedule existing =
                flightScheduleRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight schedule update failed: scheduleId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight schedule not found with id: " + id
                            );
                        });

        /*
         * Apply the requested schedule changes to the managed entity.
         *
         * Note that changing the schedule definition does not automatically
         * reconcile previously generated future FlightInstances. That lifecycle
         * should be handled separately if schedule changes must propagate.
         */
        FlightScheduleMapper.updateEntity(
                request,
                existing
        );

        FlightSchedule saved =
                flightScheduleRepository.save(existing);

        log.info(
                "Flight schedule updated successfully scheduleId={} startDate={} endDate={}",
                saved.getId(),
                saved.getStartDate(),
                saved.getEndDate()
        );

        return getFlightScheduleResponse(saved);
    }


    // ==================== Delete Operations ====================

    @Override
    @Transactional
    public void deleteFlightSchedule(
            Long id
    ) {

        log.info(
                "Deleting flight schedule scheduleId={}",
                id
        );

        FlightSchedule schedule =
                flightScheduleRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight schedule deletion failed: scheduleId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Flight schedule not found with id: " + id
                            );
                        });

        flightScheduleRepository.delete(schedule);

        log.info(
                "Flight schedule deleted successfully scheduleId={}",
                id
        );
    }


    // ==================== Response Enrichment Helper ====================

    /**
     * Builds the API response by enriching stored Airport IDs with
     * Airport details retrieved from Location Service.
     *
     * This helper does not define its own transaction boundary. It executes
     * within the calling service method's transaction context when applicable.
     */
    public FlightScheduleResponse getFlightScheduleResponse(
            FlightSchedule schedule
    ) throws AirportException {

        log.debug(
                "Enriching flight schedule response scheduleId={} departureAirportId={} arrivalAirportId={}",
                schedule.getId(),
                schedule.getDepartureAirportId(),
                schedule.getArrivalAirportId()
        );

        AirportResponse arrivalAirport =
                locationClient.getAirportById(
                        schedule.getArrivalAirportId()
                );

        AirportResponse departureAirport =
                locationClient.getAirportById(
                        schedule.getDepartureAirportId()
                );

        return FlightScheduleMapper.toResponse(
                schedule,
                arrivalAirport,
                departureAirport
        );
    }
}