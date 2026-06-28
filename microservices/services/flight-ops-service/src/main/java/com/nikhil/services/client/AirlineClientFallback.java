package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AirlineClientFallback implements AirlineClient {

    @Override
    public AirlineResponse getAirlineByOwner(Long userId) {
        return null;
    }

    @Override
    public AirlineResponse getAirlineById(Long airlineId) {
        return null;
    }

    @Override
    public AircraftResponse getAircraftById(Long id) {
        return null;
    }

    @Override
    public List<AirlineResponse> getAirlinesByIataCodes(List<String> codes) {
        return Collections.emptyList();
    }

    @Override
    public List<AirlineResponse> getAirlinesByAlliance(String alliance) {
        return Collections.emptyList();
    }
}
