package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.services.service.FareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for fare products tied to flights and cabin classes.
 * Gateway route: /api/fares/** → pricing-service (JWT required).
 * Internal callers: flight-ops-service (search), booking-service (price validation).
 * Data flow: request DTO → FareServiceImpl → FareRepository → FareResponse.
 */
@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
public class FareController {

    private final FareService fareService;

    @PostMapping
    public ResponseEntity<FareResponse> createFare(
            @Valid @RequestBody FareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fareService.createFare(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<FareResponse>> createFares(
            @Valid @RequestBody List<FareRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fareService.createFares(requests));
    }

    @GetMapping
    public ResponseEntity<?> getFares() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(fareService.getFares());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareResponse> getFareById(@PathVariable Long id) {
        return ResponseEntity.ok(fareService.getFareById(id));
    }

//    @GetMapping("/{id}/details")
//    public ResponseEntity<FareResponse> getFareByIdWithDetails(@PathVariable Long id) {
//        return ResponseEntity.ok(fareService.getFareByIdWithDetails(id));
//    }
//
//    @GetMapping("/flight/{flightId}")
//    public ResponseEntity<List<FareResponse>> getFaresByFlightId(
//            @PathVariable Long flightId) {
//        return ResponseEntity.ok(fareService.getFaresByFlightId(flightId));
//    }



//    @GetMapping("/flight/{flightId}/details")
//    public ResponseEntity<List<FareResponse>> getFaresByFlightIdWithDetails(
//            @PathVariable Long flightId) {
//        return ResponseEntity.ok(fareService.getFaresByFlightIdWithDetails(flightId));
//    }

    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<List<FareResponse>> getFaresByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId) {
        return ResponseEntity.ok(fareService.getFaresByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareResponse> updateFare(
            @PathVariable Long id,
            @Valid @RequestBody FareRequest request) {
        return ResponseEntity.ok(fareService.updateFare(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFare(@PathVariable Long id) {
        fareService.deleteFare(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the cheapest fare per flight for the requested cabin class.
     * Called internally by flight-ops-service during flight search to support
     * price range filtering across a page of results in a single batch call.
     *
     * <p>Request body: list of flight IDs (e.g. {@code [1, 2, 3]})<br>
     * Query param: {@code cabinClass=ECONOMY}
     *
     * @return map of {@code flightId → cheapest FareResponse} for that cabin class;
     *         flights with no matching fare are absent from the map
     */
    @PostMapping("/batch-by-ids")
    public ResponseEntity<Map<Long, FareResponse>> getFaresByIds(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(fareService.getFaresByIds(ids));
    }

    /**
     * Internal search endpoint consumed by flight-ops-service PricingClient Feign.
     */
    @PostMapping("/search")
            @RequestBody List<Long> flightIds,
            @RequestParam Long cabinClassId) {
        Map<Long, FareResponse> res= fareService.getLowestFarePerFlight(flightIds, cabinClassId);
        System.out.println("search fare response ------ "+res.toString());
        return ResponseEntity.ok(res);
    }

    /**
     * Per-flight lowest fare; flight-ops calls once per search result when cabin IDs differ.
     */
    @GetMapping("/lowest/flight/{flightId}/cabin-class/{cabinClassId}")
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId) {
        return ResponseEntity.ok(
                fareService.getLowestFareForFlightAndCabin(flightId, cabinClassId)
        );
    }


}
