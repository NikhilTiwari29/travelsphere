package com.nikhil.services.service.impl;

import com.nikhil.common_lib.event.FlightInstanceCreatedEvent;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.client.AirlineClient;
import com.nikhil.services.client.LocationClient;
import com.nikhil.services.event.FlightInstanceEventProducer;
import com.nikhil.services.mapper.FlightInstanceMapper;
import com.nikhil.services.model.Flight;
import com.nikhil.services.model.FlightInstance;
import com.nikhil.services.repository.FlightInstanceRepository;
import com.nikhil.services.repository.FlightRepository;
import com.nikhil.services.service.FlightInstanceService;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class FlightInstanceServiceImpl implements FlightInstanceService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final FlightRepository flightRepository;
    private final AirlineClient airlineClient;
    private final FlightInstanceEventProducer flightInstanceEventProducer;
    private final LocationClient locationClient;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "flightInstances", allEntries = true)
    public FlightInstanceResponse createFlightInstanceWithCabins(
            Long userId, FlightInstanceRequest request) throws Exception {

        Long airlineId = getAirlineForUser(userId);

        Flight flight = flightRepository.findById(request.getFlightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found"));

        AircraftResponse aircraft = getAircraftById(flight.getAircraftId());

        FlightInstance instance = FlightInstanceMapper.toEntity(request, flight);
        instance.setAirlineId(airlineId);
        instance.setFlight(flight);
        instance.setDepartureAirportId(request.getDepartureAirportId());
        instance.setArrivalAirportId(request.getArrivalAirportId());
        instance.setTotalSeats(aircraft.getTotalSeats());
        instance.setAvailableSeats(aircraft.getTotalSeats());

        FlightInstance flightInstance = flightInstanceRepository.save(instance);

//        publish event, seat service consume and create seat instance
        flightInstanceEventProducer.sendFlightInstanceCreated(
                FlightInstanceCreatedEvent.builder()
                        .flightInstanceId(flightInstance.getId())
                        .aircraftId(flight.getAircraftId())
                        .flightId(flight.getId())
                        .build()
        );
        System.out.println("Publish event for seat-service to create FlightInstanceCabins ----- ");

        return getFlightInstance(instance);
    }

    @Override
    public List<FlightInstanceResponse> getFlightInstances() {
        return flightInstanceRepository.findAll().stream()
                .map(fi-> {
                    try {
                        return getFlightInstance(fi);
                    } catch (AirportException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "flightInstances", key = "#id")
    public FlightInstanceResponse getFlightInstanceById(Long id) throws AirportException {
        FlightInstance fi = flightInstanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance not found with id: " + id));


        return getFlightInstance(fi);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightInstanceResponse> getByAirlineId(Long userId,
                                                       Long departureAirportId,
                                                       Long arrivalAirportId,
                                                       Long flightId,
                                                       LocalDate onDate,
                                                       Pageable pageable) {
        Long airlineId = getAirlineForUser(userId);
        LocalDateTime start = onDate != null ? onDate.atStartOfDay() : null;
        LocalDateTime end   = onDate != null ? onDate.plusDays(1).atStartOfDay() : null;

        return flightInstanceRepository.findByAirlineIdWithFilters(
                airlineId, departureAirportId, arrivalAirportId, flightId, start, end, pageable
        ).map(fi -> {
            try {
                return getFlightInstance(fi);
            } catch (AirportException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    @CacheEvict(cacheNames = "flightInstances", key = "#id")
    public FlightInstanceResponse updateFlightInstance(Long id, FlightInstanceRequest request)
            throws AirportException {
        FlightInstance existing = flightInstanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance not found with id: " + id));
        FlightInstanceMapper.updateEntity(request, existing);
        return getFlightInstance(flightInstanceRepository.save(existing));
    }

    @Override
    @CacheEvict(cacheNames = "flightInstances", key = "#id")
    public void deleteFlightInstance(Long id) {
        FlightInstance fi = flightInstanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance not found with id: " + id));
        flightInstanceRepository.delete(fi);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FlightInstanceResponse> getFlightInstancesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<FlightInstance> instances = flightInstanceRepository.findAllByIdInWithFlight(ids);

        Map<Long, AirlineResponse> airlineCache = new HashMap<>();
        Map<Long, AircraftResponse> aircraftCache = new HashMap<>();
        Map<Long, AirportResponse> airportCache = new HashMap<>();

        Map<Long, FlightInstanceResponse> result = new HashMap<>();
        for (FlightInstance fi : instances) {
            AirlineResponse airline = airlineCache.computeIfAbsent(fi.getAirlineId(), airlineClient::getAirlineById);
            AircraftResponse aircraft = aircraftCache.computeIfAbsent(fi.getFlight().getAircraftId(), airlineClient::getAircraftById);
            AirportResponse departure = airportCache.computeIfAbsent(fi.getDepartureAirportId(), locationClient::getAirportById);
            AirportResponse arrival = airportCache.computeIfAbsent(fi.getArrivalAirportId(), locationClient::getAirportById);
            result.put(fi.getId(), FlightInstanceMapper.toResponse(fi, aircraft, airline, departure, arrival));
        }
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AircraftResponse getAircraftById(Long aircraftId) {
        try {
            return airlineClient.getAircraftById(aircraftId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("No aircraft found for id: " + aircraftId);
        } catch (FeignException e) {
            throw new RuntimeException(
                    "Failed to fetch aircraft from airline-core-service: " + e.getMessage(), e);
        }
    }

    private Long getAirlineForUser(Long userId) {
        try {
            AirlineResponse airline = airlineClient.getAirlineByOwner(userId);
            return airline.getId();
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("No airline found for user: " + userId);
        } catch (FeignException e) {
            throw new RuntimeException(
                    "Failed to fetch airline from airline-core-service: " + e.getMessage(), e);
        }
    }

    private FlightInstanceResponse getFlightInstance(FlightInstance fi) throws AirportException {
        AirlineResponse airline         = airlineClient.getAirlineById(fi.getAirlineId());
        AirportResponse departureAirport = locationClient.getAirportById(fi.getDepartureAirportId());
        AirportResponse arrivalAirport   = locationClient.getAirportById(fi.getArrivalAirportId());
        AircraftResponse aircraftResponse=airlineClient.getAircraftById(fi.getFlight().getAircraftId());
        return FlightInstanceMapper.toResponse(fi,
                aircraftResponse, airline,
                departureAirport, arrivalAirport);
    }
}
