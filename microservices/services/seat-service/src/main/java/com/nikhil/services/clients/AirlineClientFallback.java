package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.stereotype.Component;

/*
 * Circuit-breaker fallback for AirlineClient Feign calls to airline-core-service.
 *
 * Used by SeatMapServiceImpl when resolving airline ownership from X-User-Id
 * during seat-map create/update. Returns null so callers surface a clear error
 * instead of proceeding with missing airline context.
 */
@Component
public class AirlineClientFallback implements AirlineClient {

    /*
     * Fallback when airline lookup by owner fails; SeatMapServiceImpl treats null
     * as "no airline for user".
     */
    @Override
    public AirlineResponse getAirlineByOwner(Long userId) {
        return null;
    }

    /*
     * Fallback when aircraft detail fetch fails; not currently used on hot paths.
     */
    @Override
    public AircraftResponse getAircraftById(Long id) {
        return null;
    }
}
