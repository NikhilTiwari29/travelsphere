package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.CabinClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/*
 * Feign client to seat-service for cabin-class metadata on ancillary assignments.
 * Gateway route: /api/cabin-classes/** (JWT). Validates cabinClassId when linking ancillaries.
 */
@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    /** Lists cabin classes for an aircraft when configuring flight-cabin ancillaries. */
    @GetMapping("/aircraft/{aircraftId}")
    List<CabinClassResponse> getCabinClassesByAircraftId(
            @PathVariable Long aircraftId);
}
