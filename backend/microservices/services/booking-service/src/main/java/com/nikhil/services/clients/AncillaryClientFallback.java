package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;
import com.nikhil.common_lib.payload.response.FlightMealResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AncillaryClientFallback implements AncillaryClient {

    @Override
    public double calculateAncillariesPrice(List<Long> flightCabinAncillaryIds) {
        return 0.0;
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByIds(List<Long> Ids) {
        return Collections.emptyList();
    }

    @Override
    public List<FlightMealResponse> getMealsByIds(List<Long> Ids) {
        return Collections.emptyList();
    }

    @Override
    public Double calculateMealPrice(List<Long> requests) {
        return 0.0;
    }
}
