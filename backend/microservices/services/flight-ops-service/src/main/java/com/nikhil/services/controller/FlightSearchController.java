package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.FlightSearchRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.service.FlightSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Public flight-search entry point for TravelSphere clients.
 * Gateway route: GET /api/flights/search → flight-ops-service (JWT required).
 * No direct Feign calls here; delegates to {@link FlightSearchService} which
 * orchestrates pricing, airline, location, and seat services internally.
 * Data flow: query params → FlightSearchRequest → DB filter → fare enrichment → paged results.
 */
@RestController
@RequiredArgsConstructor
public class FlightSearchController {

    private final FlightSearchService flightSearchService;



    /**
     * Searches bookable flight instances by route, date, cabin, and optional filters.
     * Called by frontend via API Gateway; response includes enriched airline/airport/fare data.
     */
    @GetMapping("/api/flights/search")
    public ResponseEntity<Page<FlightInstanceResponse>> searchFlights(
            @RequestParam Long departureAirportId,
            @RequestParam Long arrivalAirportId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam Integer passengers,
            @RequestParam CabinClassType cabinClass,
            @RequestParam(required = false) List<Long> airlines,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String departureTimeRange,
            @RequestParam(required = false) String arrivalTimeRange,
            @RequestParam(required = false) Integer maxDuration,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String alliance,
            Pageable pageable) {


        FlightSearchRequest request = FlightSearchRequest.builder()
                .departureAirportId(departureAirportId)
                .arrivalAirportId(arrivalAirportId)
                .departureDate(departureDate)
                .passengers(passengers)
                .cabinClass(cabinClass)
                .airlines(airlines)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .departureTimeRange(departureTimeRange)
                .arrivalTimeRange(arrivalTimeRange)
                .maxDuration(maxDuration)
                .alliance(alliance)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();
        Page<FlightInstanceResponse> res= flightSearchService.searchFlights(request, pageable);

        return ResponseEntity.ok(res);
    }


}
