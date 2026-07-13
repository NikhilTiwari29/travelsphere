package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightMealRequest;
import com.nikhil.common_lib.payload.response.FlightMealResponse;
import com.nikhil.services.service.FlightMealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing meal offerings configured for flights.
 *
 * A FlightMeal associates a catalog Meal with a Flight and maintains
 * flight-specific commercial attributes such as price and availability.
 *
 * The API is consumed by booking flows to retrieve available meals and
 * calculate meal charges during checkout.
 *
 * Cross-service references:
 *   - flightId references a Flight owned by flight-ops-service.
 *   - mealId references a Meal managed locally by ancillary-service.
 *
 * Gateway route:
 *   /api/flight-meals/**
 */
@Slf4j
@RestController
@RequestMapping("/api/flight-meals")
@RequiredArgsConstructor
public class FlightMealController {

    private final FlightMealService flightMealService;


    /**
     * Creates a meal offering for a flight.
     */
    @PostMapping
    public ResponseEntity<FlightMealResponse> createFlightMeal(
            @Valid @RequestBody FlightMealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to create flight meal flightId={} mealId={}",
                request.getFlightId(),
                request.getMealId()
        );

        FlightMealResponse response =
                flightMealService.create(request);

        log.info(
                "Flight meal created successfully flightMealId={} flightId={} mealId={}",
                response.getId(),
                request.getFlightId(),
                request.getMealId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple flight-meal offerings in a single request.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<FlightMealResponse>> bulkCreateFlightMeals(
            @Valid @RequestBody List<FlightMealRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Received bulk flight meal creation request count={}",
                requests.size()
        );

        List<FlightMealResponse> responses =
                flightMealService.bulkCreate(requests);

        log.info(
                "Bulk flight meal creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    /**
     * Calculates the aggregate price of selected FlightMeal records.
     *
     * Used by booking-service during checkout to calculate the meal component
     * of the booking total. The request contains FlightMeal IDs rather than
     * catalog Meal IDs because pricing is maintained at the flight-meal level.
     */
    @PostMapping("/price/total")
    public ResponseEntity<Double> calculateMealPrice(
            @RequestBody List<Long> requests
    ) {

        log.debug(
                "Received meal price calculation request itemCount={}",
                requests.size()
        );

        double totalPrice =
                flightMealService.calculateMealPrice(requests);

        log.debug(
                "Meal price calculation completed itemCount={} totalPrice={}",
                requests.size(),
                totalPrice
        );

        return ResponseEntity.ok(totalPrice);
    }


    /**
     * Retrieves a FlightMeal configuration by its unique identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightMealResponse> getFlightMealById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch flight meal flightMealId={}",
                id
        );

        FlightMealResponse response =
                flightMealService.getById(id);

        log.debug(
                "Flight meal fetched successfully flightMealId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all meal offerings configured for a flight.
     *
     * Used by booking and ancillary-selection flows to present the meal
     * products available for the selected flight.
     */
    @GetMapping("/flight/{flightId}")
    public ResponseEntity<List<FlightMealResponse>> getMealsByFlightId(
            @PathVariable Long flightId
    ) {

        log.debug(
                "Received request to fetch flight meals flightId={}",
                flightId
        );

        List<FlightMealResponse> responses =
                flightMealService.getByFlightId(flightId);

        log.debug(
                "Flight meals fetched successfully flightId={} count={}",
                flightId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Retrieves multiple FlightMeal records by their identifiers.
     *
     * Used by downstream services to resolve selected meal line items
     * without issuing one request per FlightMeal.
     */
    @GetMapping("/all")
    public ResponseEntity<List<FlightMealResponse>> getMealsByIds(
            @RequestParam List<Long> ids
    ) {

        log.debug(
                "Received request to fetch flight meals by IDs count={}",
                ids.size()
        );

        List<FlightMealResponse> responses =
                flightMealService.getAllByIds(ids);

        log.debug(
                "Flight meals fetched by IDs requestedCount={} resultCount={}",
                ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates the configuration of an existing FlightMeal.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightMealResponse> updateFlightMeal(
            @PathVariable Long id,
            @Valid @RequestBody FlightMealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update flight meal flightMealId={} flightId={} mealId={}",
                id,
                request.getFlightId(),
                request.getMealId()
        );

        FlightMealResponse response =
                flightMealService.update(id, request);

        log.info(
                "Flight meal updated successfully flightMealId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Updates only the availability status of a FlightMeal.
     *
     * This endpoint supports operational enablement or disablement of a meal
     * offering without replacing the complete FlightMeal configuration.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<FlightMealResponse> updateFlightMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update flight meal availability flightMealId={} available={}",
                id,
                available
        );

        FlightMealResponse response =
                flightMealService.updateAvailability(id, available);

        log.info(
                "Flight meal availability updated successfully flightMealId={} available={}",
                id,
                available
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an existing FlightMeal configuration.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightMeal(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to delete flight meal flightMealId={}",
                id
        );

        flightMealService.delete(id);

        log.info(
                "Flight meal deleted successfully flightMealId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}