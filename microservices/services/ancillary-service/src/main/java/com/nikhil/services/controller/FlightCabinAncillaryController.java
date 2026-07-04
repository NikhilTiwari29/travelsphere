package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightCabinAncillaryRequest;
import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;
import com.nikhil.services.service.FlightCabinAncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Attaches ancillaries to a flight + cabin class with price and quantity limits.
 * Gateway: /api/flight-cabin-ancillaries/** (JWT). booking-service AncillaryClient
 * fetches by flight/cabin/type and POST /price/total for order totals.
 */
@RestController
@RequestMapping("/api/flight-cabin-ancillaries")
@RequiredArgsConstructor
public class FlightCabinAncillaryController {

    private final FlightCabinAncillaryService service;

    @PostMapping
    public ResponseEntity<FlightCabinAncillaryResponse> create(
            @Valid @RequestBody FlightCabinAncillaryRequest request)
            throws ResourceNotFoundException {
        return ResponseEntity.ok(service.create(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> bulkCreate(
            @Valid @RequestBody List<FlightCabinAncillaryRequest> requests)
            throws ResourceNotFoundException {
        return ResponseEntity.ok(service.bulkCreate(requests));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<FlightCabinAncillaryResponse> getById(@PathVariable Long id)
            throws ResourceNotFoundException {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/all")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> getAllByIds(
            @RequestParam List<Long> Ids) {
        return ResponseEntity.ok(service.getAllByIds(Ids));
    }

    @GetMapping("/flight/{flightId:\\d+}/cabin/{cabinClassId}")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> getAllByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId) {
        return ResponseEntity.ok(service.getAllByFlightAndCabinClass(flightId, cabinClassId));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}/type/{type}")
    public ResponseEntity<FlightCabinAncillaryResponse> getByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type) throws ResourceNotFoundException {
        return ResponseEntity.ok(
                service.getByFlightIdAndCabinClassAndType(flightId, cabinClassId, type));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}/type/{type}/all")
    public ResponseEntity<?> getAllByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type) throws ResourceNotFoundException {
        return ResponseEntity.ok(
                service.getAllByFlightIdAndCabinClassAndType(flightId, cabinClassId, type));
    }



    @PutMapping("/{id:\\d+}")
    public ResponseEntity<FlightCabinAncillaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FlightCabinAncillaryRequest request)
            throws ResourceNotFoundException {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sums selected flight-cabin ancillary prices for booking-service checkout.
     */
    @PostMapping("/price/total")
    public ResponseEntity<?> calculateAncillariesPrice(
            @RequestBody List<Long> flightCabinAncillaryIds)
    {
        return ResponseEntity.ok(service.calculateAncillaryPrice(flightCabinAncillaryIds));
    }

}
