package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightRequest;
import com.nikhil.common_lib.payload.response.FlightResponse;
import com.nikhil.services.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @PostMapping
    public ResponseEntity<FlightResponse> createFlight(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightRequest request) throws AirportException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flightService.createFlight(userId, request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<FlightResponse>> createFlights(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody List<FlightRequest> requests) throws AirportException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flightService.createFlights(userId, requests));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<Long, FlightResponse>> getFlightsByIds(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(flightService.getFlightsByIds(ids));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<FlightResponse> getFlightById(@PathVariable Long id)  {
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @GetMapping("/number/{flightNumber}")
    public ResponseEntity<FlightResponse> getFlightByNumber(
            @PathVariable String flightNumber) throws AirportException {
        return ResponseEntity.ok(flightService.getFlightByNumber(flightNumber));
    }



    @GetMapping("/airline")
    public ResponseEntity<Page<FlightResponse>> getFlightsByAirline(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            Pageable pageable) {
        return ResponseEntity.ok(flightService.getFlightsByAirline(
                userId,
                departureAirportId,
                arrivalAirportId,
                pageable
        ));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<FlightResponse> updateFlight(
            @PathVariable Long id,
            @Valid @RequestBody FlightRequest request) throws AirportException {
        return ResponseEntity.ok(flightService.updateFlight(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<FlightResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam FlightStatus status) throws AirportException {
        return ResponseEntity.ok(flightService.changeStatus(id, status));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long id) {
        flightService.deleteFlight(id);
        return ResponseEntity.noContent().build();
    }

}
