package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.FareResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PricingClientFallback implements PricingClient {

    @Override
    public FareResponse getFareById(Long id) {
        return null;
    }

    @Override
    public Map<Long, FareResponse> getFaresByIds(List<Long> ids) {
        return Collections.emptyMap();
    }
}
