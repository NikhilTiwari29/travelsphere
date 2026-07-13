package com.nikhil.common_lib.payload.response;

import com.nikhil.common_lib.embeddable.Address;
import com.nikhil.common_lib.embeddable.Analytics;
import com.nikhil.common_lib.embeddable.GeoCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportResponse {

    private Long id;
    private String iataCode;
    private String name;
    private String detailedName;
    private ZoneId timeZone;
    private Address address;
    private CityResponse city;
    private GeoCode geoCode;
    private Analytics analytics;
}
