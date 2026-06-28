package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    @PostMapping("/api/seat-instances/price/total")
    Double calculateSeatPrice(@RequestBody List<Long> seatInstanceIds);

    @GetMapping("/api/seat-instances/all")
    List<SeatInstanceResponse> getAllByIds(
            @RequestParam List<Long> Ids);

}
