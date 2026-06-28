package com.nikhil.services.client;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.response.AirportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "location-service", fallback = LocationClientFallback.class)
public interface LocationClient {

    @GetMapping("/{id}")
    AirportResponse getAirportById(@PathVariable Long id) throws AirportException;
}
