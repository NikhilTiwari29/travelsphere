package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.AirportResponse;
import org.springframework.stereotype.Component;

@Component
public class LocationClientFallback implements LocationClient {

    @Override
    public AirportResponse getAirportById(Long id) {
        return null;
    }
}
