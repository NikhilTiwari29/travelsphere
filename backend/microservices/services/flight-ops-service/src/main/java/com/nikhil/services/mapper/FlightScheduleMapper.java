package com.nikhil.services.mapper;

import com.nikhil.common_lib.enums.RecurrenceType;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import com.nikhil.services.model.Flight;
import com.nikhil.services.model.FlightSchedule;

public class FlightScheduleMapper {

    public static FlightSchedule toEntity(FlightScheduleRequest request,
                                          Flight flight) {
        if (request == null) return null;
        return FlightSchedule.builder()
                .flight(flight)

                .departureAirportId(request.getDepartureAirportId() != null ?
                        request.getDepartureAirportId() : flight.getDepartureAirportId())
                .arrivalAirportId(request.getArrivalAirportId() != null ?
                        request.getArrivalAirportId() : flight.getArrivalAirportId())
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .recurrenceType(request.getRecurrenceType() != null ?
                        request.getRecurrenceType() : RecurrenceType.DAILY)
                .operatingDays(request.getOperatingDays())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
    }

    public static FlightScheduleResponse toResponse(FlightSchedule fs,
                                                    AirportResponse arrival,
                                                    AirportResponse departure) {
        if (fs == null) return null;
        return FlightScheduleResponse.builder()
                .id(fs.getId())
                .flightId(fs.getFlight() != null ? fs.getFlight().getId() : null)
                .flightNumber(fs.getFlight() != null ? fs.getFlight().getFlightNumber() : null)
                .departureAirport(departure)
                .arrivalAirport(arrival)
                .departureTime(fs.getDepartureTime())
                .arrivalTime(fs.getArrivalTime())
                .startDate(fs.getStartDate())
                .endDate(fs.getEndDate())
                .recurrenceType(fs.getRecurrenceType())
                .operatingDays(fs.getOperatingDays())
                .isActive(fs.getIsActive())
                .version(fs.getVersion())
                .build();
    }

    public static void updateEntity(FlightScheduleRequest request, FlightSchedule existing) {
        if (request == null || existing == null) return;
        if (request.getDepartureAirportId() != null) existing.setDepartureAirportId(request.getDepartureAirportId());
        if (request.getArrivalAirportId() != null) existing.setArrivalAirportId(request.getArrivalAirportId());
        if (request.getDepartureTime() != null) existing.setDepartureTime(request.getDepartureTime());
        if (request.getArrivalTime() != null) existing.setArrivalTime(request.getArrivalTime());
        if (request.getStartDate() != null) existing.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) existing.setEndDate(request.getEndDate());
        if (request.getRecurrenceType() != null) existing.setRecurrenceType(request.getRecurrenceType());
        if (request.getOperatingDays() != null) existing.setOperatingDays(request.getOperatingDays());
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
    }
}
