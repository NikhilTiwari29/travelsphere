package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FlightScheduleRequest;
import com.nikhil.common_lib.payload.response.FlightScheduleResponse;
import com.nikhil.services.service.FlightScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flight-schedules")
@RequiredArgsConstructor
public class FlightScheduleController {

    private final FlightScheduleService flightScheduleService;

    @PostMapping
    public ResponseEntity<FlightScheduleResponse> createFlightSchedule(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlightScheduleRequest request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        flightScheduleService
                                .createFlightSchedule(userId,request)
                );
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightScheduleResponse> getFlightScheduleById(@PathVariable Long id) throws AirportException {
        return ResponseEntity.ok(
                flightScheduleService.getFlightScheduleById(id)
        );
    }

    @GetMapping
    public ResponseEntity<?> getFlightSchedules(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(
                flightScheduleService.getFlightScheduleByAirline(userId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlightScheduleResponse> updateFlightSchedule(
            @PathVariable Long id,
            @Valid @RequestBody FlightScheduleRequest request) throws AirportException {
        return ResponseEntity.ok(flightScheduleService.updateFlightSchedule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightSchedule(@PathVariable Long id) {
        flightScheduleService.deleteFlightSchedule(id);
        return ResponseEntity.noContent().build();
    }

}
