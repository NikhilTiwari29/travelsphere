package com.nikhil.services.client;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.response.AirportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
 * Feign client to location-service for airport enrichment in ancillary responses.
 * Gateway route: /api/airports/** (JWT). Called when building flight-linked DTOs.
 */
@FeignClient(name = "location-service", fallback = LocationClientFallback.class)
public interface LocationClient {

    /** Fetches airport metadata by ID for display in enriched ancillary payloads. */
    @GetMapping("/{id}")
    AirportResponse getAirportById(@PathVariable Long id) throws AirportException;
}
