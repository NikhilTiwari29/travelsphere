package com.nikhil.services.controller;

import com.nikhil.common_lib.payload.request.SeatRequest;
import com.nikhil.common_lib.payload.response.SeatResponse;
import com.nikhil.services.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * REST API for template Seat rows belonging to a SeatMap.
 *
 * Gateway route: /api/seats/** → seat-service (JWT required).
 * Seats are static layout definitions; runtime availability lives in SeatInstance.
 *
 * Entity chain: SeatMap → Seat → SeatInstance (cloned per flight instance).
 */
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;



    @GetMapping
    public ResponseEntity<List<SeatResponse>> getAllSeats() {
        return ResponseEntity.ok(seatService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatResponse> getSeatById(@PathVariable Long id) {
        return ResponseEntity.ok(seatService.getSeatById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeatResponse> updateSeat(
            @PathVariable Long id,
            @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.ok(seatService.updateSeat(id, request));
    }


}
