package com.nikhil.common_lib.payload.request;

import com.nikhil.common_lib.enums.AirlineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineRequest {

    // Commercial airline code used for ticketing, booking, and passenger-facing operations (e.g., 6E, AI).
    @NotBlank
    @Size(min = 2, max = 2, message = "IATA code must be exactly 2 characters")
    private String iataCode;

    // Operational airline code used for flight planning and air traffic control (e.g., IGO, AIC).
    @NotBlank
    @Size(min = 3, max = 3, message = "ICAO code must be exactly 3 characters")
    private String icaoCode;

    @NotBlank
    private String name;

    private String alias;

    @NotBlank
    private String country;

    private String logoUrl;

    private String website;

    private AirlineStatus status;

    private String alliance;

    private Long headquartersCityId;

    private String supportEmail;
    private String supportPhone;
    private String supportHours;
}
