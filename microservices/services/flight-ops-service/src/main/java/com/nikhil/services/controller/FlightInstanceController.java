package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightInstanceRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import com.nikhil.services.service.FlightInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flight-instances")
@RequiredArgsConstructor
public class FlightInstanceController {

    private final FlightInstanceService flightInstanceService;

    @PostMapping
    public ResponseEntity<FlightInstanceResponse> createFlightInstance(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightInstanceRequest request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flightInstanceService
                        .createFlightInstanceWithCabins(userId,request));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<Long, FlightInstanceResponse>> getFlightInstancesByIds(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(flightInstanceService.getFlightInstancesByIds(ids));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<FlightInstanceResponse> getFlightInstanceById(@PathVariable Long id) throws AirportException {
        return ResponseEntity.ok(flightInstanceService.getFlightInstanceById(id));
    }

    @GetMapping("/list")
    public ResponseEntity<List<FlightInstanceResponse>> getFlightInstanceById() throws AirportException {
        return ResponseEntity.ok(flightInstanceService.getFlightInstances());
    }





    @GetMapping()
    public ResponseEntity<Page<FlightInstanceResponse>> getByAirlineId(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            @RequestParam(required = false) Long flightId,
            @RequestParam(required = false) LocalDate onDate,
            Pageable pageable) {
        return ResponseEntity.ok(flightInstanceService.getByAirlineId(
                userId,
                departureAirportId,
                arrivalAirportId,
                flightId,
                onDate,
                pageable));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<FlightInstanceResponse> updateFlightInstance(
            @PathVariable Long id,
            @Valid @RequestBody FlightInstanceRequest request) throws AirportException {
        return ResponseEntity.ok(flightInstanceService.updateFlightInstance(id, request));
    }


    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteFlightInstance(@PathVariable Long id) {
        flightInstanceService.deleteFlightInstance(id);
        return ResponseEntity.noContent().build();
    }
}
