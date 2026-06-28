package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;
import com.nikhil.common_lib.payload.response.FlightMealResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "ancillary-service", fallback = AncillaryClientFallback.class)
public interface AncillaryClient {

    @PostMapping("/api/flight-cabin-ancillaries/price/total")
    double calculateAncillariesPrice(
            @RequestBody List<Long> flightCabinAncillaryIds);

    @GetMapping("/api/flight-cabin-ancillaries/all")
    List<FlightCabinAncillaryResponse> getAllByIds(
            @RequestParam List<Long> Ids);

    @GetMapping("/api/flight-meals/all")
    List<FlightMealResponse> getMealsByIds(
            @RequestParam List<Long> Ids);

    @PostMapping("/api/flight-meals/price/total")
    Double calculateMealPrice(
            @RequestBody List<Long> requests);


}
