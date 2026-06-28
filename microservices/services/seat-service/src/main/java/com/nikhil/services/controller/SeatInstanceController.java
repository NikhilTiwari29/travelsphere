package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.payload.request.SeatInstanceRequest;
import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import com.nikhil.services.service.SeatInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seat-instances")
@RequiredArgsConstructor
public class SeatInstanceController {

    private final SeatInstanceService seatInstanceService;

    @PostMapping
    public ResponseEntity<SeatInstanceResponse> createSeatInstance(
            @Valid @RequestBody SeatInstanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seatInstanceService.createSeatInstance(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatInstanceResponse> getSeatInstanceById(@PathVariable Long id) {
        return ResponseEntity.ok(seatInstanceService.getSeatInstanceById(id));
    }

    @PostMapping("/price/total")
    public ResponseEntity<Double> calculateSeatPrice(@RequestBody List<Long> seatInstanceIds) {
        return ResponseEntity.ok(seatInstanceService.calculateSeatPrice(seatInstanceIds));
    }

    @GetMapping("/flight/{flightId}")
    public ResponseEntity<List<SeatInstanceResponse>> getSeatInstancesByFlightId(
            @PathVariable Long flightId) {
        return ResponseEntity.ok(seatInstanceService.getSeatInstancesByFlightId(flightId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<SeatInstanceResponse>> getAllByIds(
            @RequestParam List<Long> Ids) {
        return ResponseEntity.ok(seatInstanceService.getAllByIds(Ids));
    }

    @GetMapping("/flight/{flightId}/available")
    public ResponseEntity<List<SeatInstanceResponse>> getAvailableSeatsByFlightId(
            @PathVariable Long flightId) {
        return ResponseEntity.ok(seatInstanceService.getAvailableSeatsByFlightId(flightId));
    }

    @GetMapping("/flight/{flightId}/available/count")
    public ResponseEntity<Long> countAvailableByFlightId(@PathVariable Long flightId) {
        return ResponseEntity.ok(seatInstanceService.countAvailableByFlightId(flightId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SeatInstanceResponse> updateSeatInstanceStatus(
            @PathVariable Long id,
            @RequestParam SeatAvailabilityStatus status) {
        return ResponseEntity.ok(seatInstanceService.updateSeatInstanceStatus(id, status));
    }
}
