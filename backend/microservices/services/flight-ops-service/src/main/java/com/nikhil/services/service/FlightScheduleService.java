package com.nikhil.services.service;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface FlightScheduleService {

    FlightScheduleResponse createFlightSchedule(Long userId, FlightScheduleRequest request) throws Exception;
    FlightScheduleResponse getFlightScheduleById(Long id) throws AirportException;

    List<FlightScheduleResponse> getFlightScheduleByAirline(Long userId) throws AirportException;

    FlightScheduleResponse updateFlightSchedule(Long id, FlightScheduleRequest request) throws AirportException;

    void deleteFlightSchedule(Long id) throws AirportException;
}
