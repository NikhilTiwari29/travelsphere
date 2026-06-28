package com.nikhil.services.client;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.response.CabinClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    @GetMapping("api/seats/aircraft/{aircraftId}")
    List<CabinClassResponse> getCabinClassesByAircraftId(
            @PathVariable Long aircraftId);

   @GetMapping("/api/cabin-classes/aircraft/{id}/name/{cabinClass}")
   CabinClassResponse getCabinClassByAircraftIdAndName(
            @PathVariable CabinClassType cabinClass,
            @PathVariable Long id
   );
}
