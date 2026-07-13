package com.nikhil.services.service;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FlightInstanceService {

    FlightInstanceResponse createFlightInstanceWithCabins(
            Long userId,
            FlightInstanceRequest request)
            throws Exception;

    List<FlightInstanceResponse> getFlightInstances();

    FlightInstanceResponse getFlightInstanceById(Long id) throws AirportException;

    Page<FlightInstanceResponse> getByAirlineId(Long airlineId,
                                                Long departureAirportId,
                                                Long arrivalAirportId,
                                                Long flightId,
                                                LocalDate onDate,
                                                Pageable pageable);

    FlightInstanceResponse updateFlightInstance(
            Long id,
            FlightInstanceRequest request) throws AirportException;

    void deleteFlightInstance(Long id);

    Map<Long, FlightInstanceResponse> getFlightInstancesByIds(List<Long> ids);
}
