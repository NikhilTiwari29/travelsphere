package com.nikhil.services.service;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.payload.request.SeatInstanceRequest;
import com.nikhil.common_lib.payload.response.SeatInstanceResponse;

import java.util.List;

/*
 * Service contract for per-flight seat inventory and pricing.
 *
 * Backed by SeatInstanceController (/api/seat-instances/**).
 * booking-service SeatClient: calculateSeatPrice, getAllByIds.
 * updateSeatInstanceStatus also driven by booking.confirmed Kafka event.
 */
public interface SeatInstanceService {

    SeatInstanceResponse createSeatInstance(SeatInstanceRequest request);
    SeatInstanceResponse getSeatInstanceById(Long id);
    List<SeatInstanceResponse> getSeatInstancesByFlightId(Long flightId);
    List<SeatInstanceResponse> getAvailableSeatsByFlightId(Long flightId);
    List<SeatInstanceResponse> getAllByIds(List<Long> Ids);
    SeatInstanceResponse updateSeatInstanceStatus(Long id, SeatAvailabilityStatus status);
    Long countAvailableByFlightId(Long flightId);
    Double calculateSeatPrice(List<Long> seatInstanceId);
}
