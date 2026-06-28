package com.nikhil.services.mapper;

import com.nikhil.common_lib.payload.response.SeatMapResponse;
import com.nikhil.common_lib.payload.request.SeatMapRequest;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.Seat;
import com.nikhil.services.model.SeatMap;

import java.util.List;
import java.util.stream.Collectors;

public class SeatMapMapper {

    public static SeatMap toEntity(SeatMapRequest request,
                                   CabinClass cabinClass) {
        return SeatMap.builder()
                .name(request.getName())
                .totalRows(request.getTotalRows())
                .leftSeatsPerRow(request.getLeftSeatsPerRow())
                .rightSeatsPerRow(request.getRightSeatsPerRow())
                .cabinClass(cabinClass)
                .build();
    }

    public static void updateEntity(SeatMapRequest request, SeatMap seatMap) {
        seatMap.setName(request.getName());
        seatMap.setTotalRows(request.getTotalRows());
        seatMap.setLeftSeatsPerRow(request.getLeftSeatsPerRow());
        seatMap.setRightSeatsPerRow(request.getRightSeatsPerRow());
    }

    public static SeatMapResponse toResponse(SeatMap seatMap) {
        List<Seat> seats = seatMap.getSeats();

        int totalSeats = seats != null ? seats.size() : 0;
        int availableSeats = seats != null ? (int) seats.stream().filter(seat ->
                Boolean.TRUE.equals(seat.getIsAvailable()) &&
                        Boolean.TRUE.equals(seat.getIsActive()) &&
                        !Boolean.TRUE.equals(seat.getIsBlocked())).count() : 0;

        int windowSeats = seats != null ? (int) seats.stream().filter(seat ->
                seat.getSeatType().name().contains("WINDOW")).count() : 0;
        int aisleSeats = seats != null ? (int) seats.stream().filter(seat ->
                seat.getSeatType().name().contains("AISLE")).count() : 0;
        int middleSeats = seats != null ? (int) seats.stream().filter(seat ->
                seat.getSeatType().name().contains("MIDDLE")).count() : 0;
        int premiumSeats = seats != null ? (int) seats.stream().filter(seat ->
                Boolean.TRUE.equals(seat.getHasExtraLegroom()) ||
                        Boolean.TRUE.equals(seat.getIsEmergencyExit()) ||
                        Boolean.TRUE.equals(seat.getHasExtraWidth())).count() : 0;
        int emergencyExitSeats = seats != null ? (int) seats.stream().filter(seat ->
                Boolean.TRUE.equals(seat.getIsEmergencyExit())).count() : 0;

        return SeatMapResponse.builder()
                .id(seatMap.getId())
                .name(seatMap.getName())
                .totalRows(seatMap.getTotalRows())
                .leftSeatsPerRow(seatMap.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMap.getRightSeatsPerRow())
                .airlineId(seatMap.getAirlineId())
                .cabinClassId(seatMap.getCabinClass() != null ? seatMap.getCabinClass().getId() : null)
                .cabinClassName(seatMap.getCabinClass() != null ? seatMap.getCabinClass().getName().toString() : null)
                .cabinClassCode(seatMap.getCabinClass() != null ? seatMap.getCabinClass().getCode() : null)
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .occupiedSeats(totalSeats - availableSeats)
                .seats(seats != null ? seats.stream().map(SeatMapper::toResponse)
                        .collect(Collectors.toList()) : null)
                .windowSeats(windowSeats)
                .aisleSeats(aisleSeats)
                .middleSeats(middleSeats)
                .premiumSeats(premiumSeats)
                .emergencyExitSeats(emergencyExitSeats)
                .build();
    }

    public static SeatMapResponse toSimpleResponse(SeatMap seatMap) {
        return SeatMapResponse.builder()
                .totalRows(seatMap.getTotalRows())
                .leftSeatsPerRow(seatMap.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMap.getRightSeatsPerRow())
                .build();
    }
}
