package com.nikhil.services.mapper;

import com.nikhil.common_lib.payload.response.FlightMealResponse;
import com.nikhil.services.model.FlightMeal;

public class FlightMealMapper {

    public static FlightMealResponse toResponse(FlightMeal flightMeal) {
        if (flightMeal == null) {
            return null;
        }

        return FlightMealResponse.builder()
                .id(flightMeal.getId())
                .flightId(flightMeal.getFlightId())
                .meal(MealMapper.toResponse(flightMeal.getMeal()))
                .available(flightMeal.getAvailable())
                .price(flightMeal.getPrice())
                .displayOrder(flightMeal.getDisplayOrder())
                .build();
    }
}
