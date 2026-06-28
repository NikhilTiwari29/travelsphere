package com.nikhil.services.mapper;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.payload.request.SeatInstanceRequest;
import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import com.nikhil.services.model.FlightInstanceCabin;
import com.nikhil.services.model.Seat;
import com.nikhil.services.model.SeatInstance;

public class SeatInstanceMapper {

    public static SeatInstance toEntity(SeatInstanceRequest request, Seat seat,
                                        FlightInstanceCabin flightInstanceCabin) {
        return SeatInstance.builder()
                .flightId(request.getFlightId())
                .seat(seat)
                .flightInstanceCabin(flightInstanceCabin)
                .flightInstanceId(request.getFlightInstanceId())
                .status(request.getStatus() != null ?
                        SeatAvailabilityStatus.valueOf(request.getStatus().toUpperCase()) :
                        SeatAvailabilityStatus.AVAILABLE)
                .mealPreference(request.getMealPreference())
                .fare(request.getFare())
                .flightScheduleId(request.getFlightScheduleId())
                .build();
    }

    public static SeatInstanceResponse toResponse(SeatInstance si) {
        return SeatInstanceResponse.builder()
                .id(si.getId())
                .flightId(si.getFlightId())
                .seatId(si.getSeat() != null ? si.getSeat().getId() : null)
                .seatNumber(si.getSeat() != null ? si.getSeat().getSeatNumber() : null)
                .seatType(si.getSeat() != null ? si.getSeat().getSeatType().name() : null)
                .seatPosition(si.getSeat() != null ? si.getSeat().getFullPosition() : null)
                .seat(SeatMapper.toResponse(si.getSeat()))
                .status(si.getStatus())
                .flightInstanceId(si.getFlightInstanceId())
                .flightCabinId(si.getFlightInstanceCabin() != null ? si.getFlightInstanceCabin().getId() : null)
                .mealPreference(si.getMealPreference())
                .fare(si.getFare())
                .price(si.getPremiumSurcharge())
                .version(si.getVersion())
                .createdAt(si.getCreatedAt())
                .updatedAt(si.getUpdatedAt())
                .isAvailable(si.isAvailable())
                .isBooked(si.isBooked())
                .isOccupied(si.getStatus() == SeatAvailabilityStatus.OCCUPIED)
                .seatCharacteristics(
                        si.getSeat() != null ? si.getSeat().getSeatCharacteristics() : null)
                .build();
    }
}
