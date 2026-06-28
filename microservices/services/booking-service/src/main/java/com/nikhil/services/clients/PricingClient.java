package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.FareResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "pricing-service", fallback = PricingClientFallback.class)
public interface PricingClient {

    @GetMapping("/api/fares/{id}")
    FareResponse getFareById(@PathVariable Long id);

    @PostMapping("/api/fares/batch-by-ids")
    Map<Long, FareResponse> getFaresByIds(@RequestBody List<Long> ids);
}
