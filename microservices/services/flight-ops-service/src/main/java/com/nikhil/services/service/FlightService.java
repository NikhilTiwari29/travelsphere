package com.nikhil.services.service;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightRequest;
import com.nikhil.common_lib.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface FlightService {

    FlightResponse createFlight(Long userId, FlightRequest request) throws AirportException;
    List<FlightResponse> createFlights(Long userId, List<FlightRequest> requests) throws AirportException;
    FlightResponse getFlightById(Long id);
    FlightResponse getFlightByNumber(String flightNumber) throws AirportException;
    Page<FlightResponse> getFlightsByAirline(Long userId,
                                             Long departureAirportId,
                                             Long arrivalAirportId,
                                             Pageable pageable);
    FlightResponse updateFlight(Long id, FlightRequest request) throws AirportException;
    FlightResponse changeStatus(Long id, FlightStatus status) throws AirportException;
    void deleteFlight(Long id);

    Map<Long, FlightResponse> getFlightsByIds(List<Long> ids);
}
