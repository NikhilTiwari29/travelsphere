package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/*
 * Feign client to airline-core-service for airline ownership validation.
 * Used by meal/ancillary create flows to resolve airlineId from X-User-Id.
 * Gateway exposes /api/airlines/** publicly; internal calls use Eureka + LoadBalancer.
 */
@FeignClient(name = "airline-core-service", fallback = AirlineClientFallback.class)
public interface AirlineClient {

    /** Maps authenticated airline owner to their Airline record. */
    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(@RequestHeader("X-User-Id") Long userId);

    /** Optional aircraft lookup when validating flight-specific ancillary setup. */
    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable("id") Long id);
}
