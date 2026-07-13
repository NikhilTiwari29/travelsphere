package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.FareResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign client to pricing-service for fare lookup during flight search.
 * Called by FlightSearchServiceImpl (not exposed via Gateway from flight-ops).
 * Endpoints: POST /api/fares/search, GET /api/fares/lowest/flight/{id}/cabin-class/{id}.
 * Fallback factory returns empty fares so search degrades gracefully on outage.
 */
@FeignClient(name = "pricing-service", fallbackFactory = PricingClientFallbackFactory.class)
public interface PricingClient {

    /**
     * Batch lowest-fare map for a page of flights and one cabin class ID.
     */
    @PostMapping("/api/fares/search")
    Map<Long, FareResponse> getLowestFarePerFlight(
            @RequestBody List<Long> flightIds,
            @RequestParam("cabinClassId") Long cabinClassId);

    /**
     * Single flight + cabin lowest fare; used when aircraft-specific cabin IDs differ.
     */
    @GetMapping("/api/fares/lowest/flight/{flightId}/cabin-class/{cabinClassId}")
    FareResponse getLowestFareForFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId
    );
}
