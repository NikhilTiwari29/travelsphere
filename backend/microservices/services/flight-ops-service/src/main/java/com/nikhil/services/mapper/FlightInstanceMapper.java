package com.nikhil.services.mapper;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.model.Flight;
import com.nikhil.services.model.FlightInstance;

public class FlightInstanceMapper {

    public static FlightInstance toEntity(FlightInstanceRequest request, Flight flight) {
        if (request == null) return null;
        return FlightInstance.builder()
                .flight(flight)
                .airlineId(request.getAirlineId() != null ? request.getAirlineId() : flight.getAirlineId())
                .scheduleId(request.getScheduleId())
                .departureAirportId(request.getDepartureAirportId() != null ?
                        request.getDepartureAirportId() : flight.getDepartureAirportId())
                .arrivalAirportId(request.getArrivalAirportId() != null ?
                        request.getArrivalAirportId() : flight.getArrivalAirportId())
                .departureDateTime(request.getDepartureDateTime())
                .arrivalDateTime(request.getArrivalDateTime())
                .totalSeats(request.getTotalSeats())
                .availableSeats(request.getAvailableSeats() != null ?
                        request.getAvailableSeats() : request.getTotalSeats())
                .status(request.getStatus() != null ? request.getStatus() : FlightStatus.SCHEDULED)
                .minAdvanceBookingDays(request.getMinAdvanceBookingDays())
                .maxAdvanceBookingDays(request.getMaxAdvanceBookingDays())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .terminal(request.getTerminal())
                .gate(request.getGate())
                .build();
    }

    public static FlightInstanceResponse toResponse(FlightInstance fi,
                                                    AircraftResponse aircraftResponse,
                                                    AirlineResponse airline,
                                                    AirportResponse departureAirport,
                                                    AirportResponse arrivalAirport) {
        if (fi == null) return null;
        return FlightInstanceResponse.builder()
                .id(fi.getId())
                .flightId(fi.getFlight() != null ? fi.getFlight().getId() : null)
                .flightNumber(fi.getFlight() != null ? fi.getFlight().getFlightNumber() : null)
                .aircraftId(fi.getFlight().getAircraftId())
                .aircraftModal(aircraftResponse.getModel())
                .aircraftCode(aircraftResponse.getCode())
                .airlineId(fi.getAirlineId())
                .airlineName(airline.getName())
                .airlineLogo(airline.getLogoUrl())
                .departureAirport(departureAirport)
                .arrivalAirport(arrivalAirport)
                .departureDateTime(fi.getDepartureDateTime())
                .arrivalDateTime(fi.getArrivalDateTime())
                .formattedDuration(fi.getFormattedDuration())
                .totalSeats(fi.getTotalSeats())
                .availableSeats(fi.getAvailableSeats())
                .status(fi.getStatus())
                .minAdvanceBookingDays(fi.getMinAdvanceBookingDays())
                .maxAdvanceBookingDays(fi.getMaxAdvanceBookingDays())
                .isActive(fi.getIsActive())
                .terminal(fi.getTerminal())
                .gate(fi.getGate())
                .version(fi.getVersion())
                .build();
    }

    public static void updateEntity(FlightInstanceRequest request, FlightInstance existing) {
        if (request == null || existing == null) return;
        if (request.getDepartureAirportId() != null) existing.setDepartureAirportId(request.getDepartureAirportId());
        if (request.getArrivalAirportId() != null) existing.setArrivalAirportId(request.getArrivalAirportId());
        if (request.getDepartureDateTime() != null) existing.setDepartureDateTime(request.getDepartureDateTime());
        if (request.getArrivalDateTime() != null) existing.setArrivalDateTime(request.getArrivalDateTime());
        if (request.getTotalSeats() != null) existing.setTotalSeats(request.getTotalSeats());
        if (request.getAvailableSeats() != null) existing.setAvailableSeats(request.getAvailableSeats());
        if (request.getStatus() != null) existing.setStatus(request.getStatus());
        if (request.getMinAdvanceBookingDays() != null) existing.setMinAdvanceBookingDays(request.getMinAdvanceBookingDays());
        if (request.getMaxAdvanceBookingDays() != null) existing.setMaxAdvanceBookingDays(request.getMaxAdvanceBookingDays());
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        if (request.getTerminal() != null) existing.setTerminal(request.getTerminal());
        if (request.getGate() != null) existing.setGate(request.getGate());
    }
}
