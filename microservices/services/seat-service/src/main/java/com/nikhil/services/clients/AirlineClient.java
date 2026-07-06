package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for synchronous communication with Airline Core Service.
 *
 * Seat Service uses this client to retrieve airline ownership information
 * and aircraft details required during seat-map configuration and
 * flight-specific seat generation.
 *
 * Service discovery resolves the logical service name
 * "airline-core-service" to an available service instance.
 *
 * AirlineClientFallback provides fallback behavior when Airline Core Service
 * is unavailable or the remote call cannot be completed.
 */
@FeignClient(
        name = "airline-core-service",
        fallback = AirlineClientFallback.class
)
public interface AirlineClient {

    /**
     * Retrieves the airline associated with the authenticated user.
     *
     * The X-User-Id header is propagated to Airline Core Service, where
     * the user ID is used to resolve the airline owned by that user.
     *
     * Used by Seat Service to associate seat-map operations with the
     * correct airline.
     *
     * @param userId authenticated user ID propagated by the API Gateway
     * @return airline associated with the specified owner
     */
    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(
            @RequestHeader("X-User-Id") Long userId
    );

    /**
     * Retrieves aircraft details by aircraft ID.
     *
     * Used to validate that the aircraft exists and to obtain aircraft
     * configuration data required when creating or cloning seat layouts
     * for flight-specific seat instances.
     *
     * @param id aircraft ID
     * @return aircraft details
     */
    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(
            @PathVariable("id") Long id
    );
}