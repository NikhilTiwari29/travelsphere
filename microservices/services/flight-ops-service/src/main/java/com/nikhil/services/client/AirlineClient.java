package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/*
 * Feign client to airline-core-service for airline and aircraft master data.
 * Callers: FlightSearchServiceImpl, FlightInstanceServiceImpl, FlightScheduleServiceImpl.
 * Gateway exposes /api/airlines/** publicly; this client uses internal Eureka routing.
 * Resolves owner airline (X-User-Id), IATA/alliance filters, and aircraft seat counts.
 */
@FeignClient(name = "airline-core-service", fallback = AirlineClientFallback.class)
public interface AirlineClient {

    /** Resolves airline owned by authenticated user; used for airline-scoped writes. */
    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(@RequestHeader("X-User-Id") Long userId);

    /** Enriches search/instance responses with airline branding and alliance. */
    @GetMapping("/api/airlines/{airlineId}")
    AirlineResponse getAirlineById(@PathVariable Long airlineId);

    /** Fetches seat capacity and model when creating instances from schedules. */
    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable("id") Long id);

    /**
     * Bulk IATA → airline lookup for optional search filter (Phase 1 resolution).
     */
    @GetMapping("/api/airlines/by-iata")
    List<AirlineResponse> getAirlinesByIataCodes(@RequestParam("codes") List<String> codes);

    /** Resolves alliance name to airline IDs for search filtering. */
    @GetMapping("/api/airlines/by-alliance")
    List<AirlineResponse> getAirlinesByAlliance(@RequestParam("alliance") String alliance);
}
