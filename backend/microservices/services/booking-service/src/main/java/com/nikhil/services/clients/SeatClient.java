package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Feign client used by Booking Service to price selected seats and retrieve
 * seat-instance details from Seat Service.
 */
@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    /** Sums seat surcharges during createBooking pricing. */
    @PostMapping("/api/seat-instances/price/total")
    Double calculateSeatPrice(@RequestBody List<Long> seatInstanceIds);

    /** Batch lookup of seat labels/types for booking detail responses. */
    @GetMapping("/api/seat-instances/all")
    List<SeatInstanceResponse> getAllByIds(
            @RequestParam List<Long> Ids);

}
