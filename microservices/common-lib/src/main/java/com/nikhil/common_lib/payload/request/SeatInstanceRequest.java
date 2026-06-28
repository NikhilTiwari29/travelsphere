package com.nikhil.common_lib.payload.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatInstanceRequest {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Flight instance ID is required")
    private Long flightInstanceId;

    @NotNull
    private Long flightInstanceCabinId;

    @NotNull
    private Long seatId;

    private String status;
    private String mealPreference;
    private Double fare;
    private Long flightScheduleId;
}
