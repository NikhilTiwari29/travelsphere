package com.nikhil.services.service;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightInstanceCabinRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceCabinResponse;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/*
 * Service contract for cabin-level buckets on a flight instance.
 *
 * Backed by FlightInstanceCabinController (/api/flight-instance-cabins/**).
 * Provisions SeatInstances from SeatMap seats; also triggered by
 * flight-instance-created Kafka event from flight-ops-service.
 */
public interface FlightInstanceCabinService {

    FlightInstanceCabinResponse createFlightInstanceCabin(FlightInstanceCabinRequest request) throws ResourceNotFoundException;
    FlightInstanceCabinResponse getFlightInstanceCabinById(Long id);
    Page<FlightInstanceCabinResponse> getByFlightInstanceId(
            Long flightInstanceId, Pageable pageable);
    FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(Long flightInstanceId, Long cabinClassId);
    FlightInstanceCabinResponse updateFlightInstanceCabin(Long id, FlightInstanceCabinRequest request);
    void deleteFlightInstanceCabin(Long id);
}
