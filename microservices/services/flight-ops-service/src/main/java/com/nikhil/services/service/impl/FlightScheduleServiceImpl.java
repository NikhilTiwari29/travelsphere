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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FlightScheduleServiceImpl implements FlightScheduleService {

    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightRepository flightRepository;
    private final FlightInstanceService flightInstanceService;
    private final AirlineIntegrationService airlineIntegrationService;
    private final LocationClient locationClient;

    @Override
    public FlightScheduleResponse createFlightSchedule(Long userId, FlightScheduleRequest request) throws Exception {
        Flight flight = flightRepository.findById(request.getFlightId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight not found with id: " + request.getFlightId()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        FlightSchedule schedule = FlightScheduleMapper.toEntity(
                request, flight);
        FlightSchedule savedSchedule = flightScheduleRepository.save(schedule);

//      Generate FlightInstances For schedule

        System.out.println("saved schedule: " + savedSchedule.getId());

        AircraftResponse aircraft=airlineIntegrationService.getAircraftById(flight.getAircraftId());

        List<DayOfWeek> operatingDays = schedule.getOperatingDays(); // e.g., MON, WED, FRI
        LocalDate startDate = schedule.getStartDate();
        LocalDate endDate = schedule.getEndDate();

        FlightInstanceRequest flightInstanceRequest=FlightInstanceRequest.builder()
                .scheduleId(savedSchedule.getId())
                .flightId(flight.getId())
                .arrivalAirportId(flight.getArrivalAirportId())
                .departureAirportId(flight.getDepartureAirportId())
                .totalSeats(aircraft.getTotalSeats())
                .status(FlightStatus.SCHEDULED)
                .build();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (operatingDays.contains(date.getDayOfWeek())) {
                flightInstanceRequest.setDepartureDateTime(
                        LocalDateTime.of(date, schedule.getDepartureTime()));
                flightInstanceRequest.setArrivalDateTime(
                        LocalDateTime.of(date, schedule.getArrivalTime()));
                System.out.println("flightInstanceRequest: " + flightInstanceRequest.getScheduleId());
                flightInstanceService.createFlightInstanceWithCabins(
                        userId,flightInstanceRequest);

            }
        }
        return getFlightScheduleResponse(savedSchedule);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightScheduleResponse getFlightScheduleById(Long id) throws AirportException {
        FlightSchedule schedule = flightScheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight schedule not found with id: " + id));
        return getFlightScheduleResponse(schedule);
    }

    @Override
    public List<FlightScheduleResponse> getFlightScheduleByAirline(Long userId) {
        Long airlineId=airlineIntegrationService.getAirlineIdForUser(userId);

        System.out.println("airline id ----- "+airlineId);
        List<FlightSchedule> schedules=flightScheduleRepository
                .findByFlightAirlineId(airlineId);
        return schedules
                .stream()
                .map(
                        schedule -> {
                            try {
                                return getFlightScheduleResponse(schedule);
                            } catch (AirportException e) {
                                throw new RuntimeException(e);
                            }
                        }
                ).collect(Collectors.toList());
    }


    @Override
    public FlightScheduleResponse updateFlightSchedule(Long id, FlightScheduleRequest request) throws AirportException {
        FlightSchedule existing = flightScheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight schedule not found with id: " + id));

        FlightScheduleMapper.updateEntity(request, existing);
        FlightSchedule saved = flightScheduleRepository.save(existing);
        return getFlightScheduleResponse(saved);
    }



    @Override
    public void deleteFlightSchedule(Long id) {
        FlightSchedule schedule = flightScheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight schedule not found with id: " + id));
        flightScheduleRepository.delete(schedule);
    }

    public FlightScheduleResponse getFlightScheduleResponse(
            FlightSchedule schedule) throws AirportException {
        AirportResponse arrivalAirport=locationClient.getAirportById(schedule.getArrivalAirportId());
        AirportResponse departureAirport=locationClient.getAirportById(schedule.getDepartureAirportId());
        return FlightScheduleMapper.toResponse(schedule,
                arrivalAirport,departureAirport);
    }
}
