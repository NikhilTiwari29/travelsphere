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

@Service
@RequiredArgsConstructor
@Transactional
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;

    @Override
    public FlightResponse createFlight(Long userId, FlightRequest request) throws AirportException {
        if (flightRepository.existsByFlightNumber(request.getFlightNumber())) {
            throw new IllegalArgumentException(
                    "Flight with number '" + request.getFlightNumber() + "' already exists");
        }

        Long airlineId = getAirlineForUser(userId);
        validateAircraftExists(request.getAircraftId());

        Flight flight = FlightMapper.toEntity(request);
        flight.setAirlineId(airlineId);
        Flight saved = flightRepository.save(flight);
        return getFlightResponse(saved);
    }

    @SneakyThrows
    @Override
    public List<FlightResponse> createFlights(Long userId, List<FlightRequest> requests) throws AirportException {
        Long airlineId = getAirlineForUser(userId);

        // Single DB call to find all already-existing flight numbers
        Set<String> existingNumbers = flightRepository.findExistingFlightNumbers(
                requests.stream().map(FlightRequest::getFlightNumber).collect(Collectors.toList()));

        // Validate each unique aircraftId once via Feign
        Set<Long> validatedAircraftIds = new HashSet<>();

        List<Flight> toSave = requests.stream()
                .filter(req -> !existingNumbers.contains(req.getFlightNumber()))
                .map(req -> {
                    if (validatedAircraftIds.add(req.getAircraftId())) {
                        validateAircraftExists(req.getAircraftId());
                    }
                    Flight flight = FlightMapper.toEntity(req);
                    flight.setAirlineId(airlineId);
                    return flight;
                })
                .collect(Collectors.toList());

        List<Flight> saved = flightRepository.saveAll(toSave);

        // Enrich responses with cached Feign lookups to avoid N+1
        AirlineResponse airline = airlineClient.getAirlineById(airlineId);
        Map<Long, AircraftResponse> aircraftCache = new HashMap<>();
        Map<Long, AirportResponse> airportCache = new HashMap<>();

        List<FlightResponse> responses = new ArrayList<>();
        for (Flight flight : saved) {
            AircraftResponse aircraft = aircraftCache.computeIfAbsent(
                    flight.getAircraftId(), airlineClient::getAircraftById);
            AirportResponse departure = airportCache.computeIfAbsent(
                    flight.getDepartureAirportId(), this::getAirport);

            AirportResponse arrival = airportCache.computeIfAbsent(
                    flight.getArrivalAirportId(), this::getAirport);

            responses.add(FlightMapper.toResponse(flight, aircraft, airline, departure, arrival));
        }
        return responses;
    }

    private AirportResponse getAirport(Long id) {

            return locationClient.getAirportById(id);

    }


    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlightById(Long id)  {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight not found with id: " + id));

        return getFlightResponse(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightResponse getFlightByNumber(String flightNumber) throws AirportException {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight not found with number: " + flightNumber));
        return getFlightResponse(flight);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> getFlightsByAirline(Long userId, Long departureAirportId, Long arrivalAirportId, Pageable pageable) {
        Long airlineId = getAirlineForUser(userId);
        return flightRepository.findByAirlineIdAndOptionalRoute(
                        airlineId,
                        departureAirportId,
                        arrivalAirportId,
                        pageable
                )
                .map(this::getFlightResponse);
    }

    private Long getAirlineForUser(Long userId) {
        try {
            AirlineResponse airline = airlineClient.getAirlineByOwner(userId);
            return airline.getId();
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("No airline found for user: " + userId);
        } catch (FeignException e) {
            throw new RuntimeException("Failed to fetch airline from airline-core-service: " + e.getMessage(), e);
        }
    }

    private void validateAircraftExists(Long aircraftId) {
        try {
            airlineClient.getAircraftById(aircraftId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Aircraft not found with id: " + aircraftId);
        } catch (FeignException e) {
            throw new RuntimeException("Failed to validate aircraft from airline-core-service: " + e.getMessage(), e);
        }
    }

    @Override
    public FlightResponse updateFlight(Long id, FlightRequest request) throws AirportException {
        Flight existing = flightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight not found with id: " + id));

        if (request.getFlightNumber() != null &&
                flightRepository.existsByFlightNumberAndIdNot(request.getFlightNumber(), id)) {
            throw new IllegalArgumentException(
                    "Flight with number '" + request.getFlightNumber() + "' already exists");
        }

        FlightMapper.updateEntity(request, existing);
        Flight saved = flightRepository.save(existing);
        return getFlightResponse(saved);
    }

    @Override
    public FlightResponse changeStatus(Long id, FlightStatus status) throws AirportException {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight not found with id: " + id));
        flight.setStatus(status);
        return getFlightResponse(flight);
    }

    @Override
    public void deleteFlight(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight not found with id: " + id));
        flightRepository.delete(flight);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FlightResponse> getFlightsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<Flight> flights = flightRepository.findAllById(ids);

        Map<Long, AirlineResponse> airlineCache = new HashMap<>();
        Map<Long, AircraftResponse> aircraftCache = new HashMap<>();
        Map<Long, AirportResponse> airportCache = new HashMap<>();

        Map<Long, FlightResponse> result = new HashMap<>();
        for (Flight flight : flights) {
            AirlineResponse airline = airlineCache.computeIfAbsent(flight.getAirlineId(), airlineClient::getAirlineById);
            AircraftResponse aircraft = aircraftCache.computeIfAbsent(flight.getAircraftId(), airlineClient::getAircraftById);
            AirportResponse departure = airportCache.computeIfAbsent(flight.getDepartureAirportId(), this::getAirport);
            AirportResponse arrival = airportCache.computeIfAbsent(flight.getArrivalAirportId(), this::getAirport);
            result.put(flight.getId(), FlightMapper.toResponse(flight, aircraft, airline, departure, arrival));
        }
        return result;
    }

    private FlightResponse getFlightResponse(Flight flight) {
        AircraftResponse aircraft=airlineClient.getAircraftById(flight.getAircraftId());
        AirlineResponse airline=airlineClient.getAirlineById(flight.getAirlineId());
        AirportResponse departureAirport=locationClient.getAirportById(flight.getDepartureAirportId());
        AirportResponse arrivalAirport=locationClient.getAirportById(flight.getArrivalAirportId());
        return FlightMapper.toResponse(flight,aircraft,airline,departureAirport,arrivalAirport);
    }
}
