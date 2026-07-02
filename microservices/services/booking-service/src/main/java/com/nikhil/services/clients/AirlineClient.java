package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/*
 * Feign client used by Booking Service to resolve the airline owned by the
 * authenticated admin user.
 */
@FeignClient(name="airline-core-service")
public interface AirlineClient {

    /** Maps airline-admin JWT user to airlineId for scoped booking queries. */
    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(@RequestHeader("X-User-Id") Long userId);


}
