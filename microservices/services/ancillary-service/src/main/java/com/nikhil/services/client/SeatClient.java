package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.CabinClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    @GetMapping("/aircraft/{aircraftId}")
    List<CabinClassResponse> getCabinClassesByAircraftId(
            @PathVariable Long aircraftId);
}
