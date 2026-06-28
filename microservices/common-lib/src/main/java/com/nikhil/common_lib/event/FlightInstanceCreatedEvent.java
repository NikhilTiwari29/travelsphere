package com.nikhil.common_lib.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInstanceCreatedEvent {
    private Long flightInstanceId;
    private Long aircraftId;
    private Long flightId;
}
