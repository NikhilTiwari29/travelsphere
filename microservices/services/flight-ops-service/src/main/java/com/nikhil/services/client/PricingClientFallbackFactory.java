package com.nikhil.services.client;

import com.nikhil.common_lib.payload.response.FareResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PricingClientFallbackFactory implements FallbackFactory<PricingClient> {

    @Override
    public PricingClient create(Throwable cause) {
        log.error("pricing-service call failed – fare/cabin filter will be skipped: {}", cause.getMessage(), cause);
        return new PricingClient() {
            @Override
            public Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId) {
                return Collections.emptyMap();
            }

            @Override
            public FareResponse getLowestFareForFlightAndCabinClass(Long flightId, Long cabinClassId) {
                return null;
            }
        };
    }
}
