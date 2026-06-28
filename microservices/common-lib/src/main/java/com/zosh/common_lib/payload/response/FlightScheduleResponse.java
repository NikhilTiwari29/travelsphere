package com.nikhil.common_lib.payload.response;

import com.nikhil.common_lib.enums.RecurrenceType;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightScheduleResponse {

    private Long id;

    private Long flightId;
    private String flightNumber;

    private AirportResponse departureAirport;
    private AirportResponse arrivalAirport;

    private LocalTime departureTime;
    private LocalTime arrivalTime;

    private LocalDate startDate;
    private LocalDate endDate;

    private RecurrenceType recurrenceType;
    private List<DayOfWeek> operatingDays;

    private Boolean isActive;
    private Long version;
}
