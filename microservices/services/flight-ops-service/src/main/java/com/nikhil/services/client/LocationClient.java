package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.AirportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
 * Feign client to location-service for airport reference data.
 * Callers: FlightSearchServiceImpl, FlightInstanceServiceImpl, FlightScheduleServiceImpl.
 * Gateway route: /api/airports/** (JWT). Stores only airport IDs locally.
 * Enriches responses with IATA codes, city names, and time zones for display.
 */
@FeignClient(name = "location-service", fallback = LocationClientFallback.class)
public interface LocationClient {

    /** Loads airport details by ID for departure/arrival enrichment. */
    @GetMapping("/api/airports/{id}")
    AirportResponse getAirportById(@PathVariable Long id);
}
