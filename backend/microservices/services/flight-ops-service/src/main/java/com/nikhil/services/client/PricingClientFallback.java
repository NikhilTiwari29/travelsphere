package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.FareResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PricingClientFallback implements PricingClient {

    @Override
    public Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId) {
        return Collections.emptyMap();
    }

    @Override
    public FareResponse getLowestFareForFlightAndCabinClass(Long flightId, Long cabinClassId) {
        return null;
    }
}
