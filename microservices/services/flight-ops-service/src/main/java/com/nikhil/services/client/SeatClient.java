package com.nikhil.services.client;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.response.CabinClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/*
 * Feign client to seat-service for cabin-class resolution during search.
 * Gateway route: /api/cabin-classes/** (JWT). Maps aircraft + cabin type → cabinClassId.
 * Used before pricing calls so fares can be filtered by cabin and price range.
 */
@FeignClient(name = "seat-service", fallback = SeatClientFallback.class)
public interface SeatClient {

    /** Lists all cabin classes configured on an aircraft layout. */
    @GetMapping("api/seats/aircraft/{aircraftId}")
    List<CabinClassResponse> getCabinClassesByAircraftId(
            @PathVariable Long aircraftId);

   /** Resolves cabinClassId for a given aircraft and CabinClassType enum name. */
   @GetMapping("/api/cabin-classes/aircraft/{id}/name/{cabinClass}")
   CabinClassResponse getCabinClassByAircraftIdAndName(
            @PathVariable CabinClassType cabinClass,
            @PathVariable Long id
   );
}
