package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.MealRequest;
import com.nikhil.common_lib.payload.response.MealResponse;
import com.nikhil.services.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing airline-owned meal catalog definitions.
 *
 * Meal represents a reusable catalog product owned by an airline.
 * Flight-specific price and availability are managed separately through
 * the FlightMeal domain.
 *
 * Domain relationship:
 *
 * Airline
 *    ↓
 * Meal Catalog
 *    ↓
 * FlightMeal
 *    ↓
 * Flight
 *
 * Ownership is resolved from the authenticated user through X-User-Id.
 *
 * Gateway route:
 *   /api/meals/**
 */
@Slf4j
@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;


    /**
     * Creates a meal definition in the authenticated airline's catalog.
     */
    @PostMapping
    public ResponseEntity<MealResponse> createMeal(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody MealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to create meal userId={}",
                userId
        );

        MealResponse response =
                mealService.create(userId, request);

        log.info(
                "Meal created successfully mealId={} userId={}",
                response.getId(),
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple meal definitions for the authenticated airline
     * in a single request.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<MealResponse>> bulkCreateMeals(
            @Valid @RequestBody List<MealRequest> requests,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received bulk meal creation request userId={} requestedCount={}",
                userId,
                requests.size()
        );

        List<MealResponse> responses =
                mealService.bulkCreate(userId, requests);

        log.info(
                "Bulk meal creation completed userId={} requestedCount={} createdCount={}",
                userId,
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    /**
     * Retrieves a meal catalog entry by its unique identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MealResponse> getMealById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch meal mealId={}",
                id
        );

        MealResponse response =
                mealService.getById(id);

        log.debug(
                "Meal fetched successfully mealId={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all meal catalog entries owned by the authenticated airline.
     *
     * The service resolves the airline identifier from X-User-Id and restricts
     * the result set to the corresponding airline catalog.
     */
    @GetMapping("/airline")
    public ResponseEntity<List<MealResponse>> getMealsByAirlineId(
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Received request to fetch airline meal catalog userId={}",
                userId
        );

        List<MealResponse> responses =
                mealService.getByAirlineId(userId);

        log.debug(
                "Airline meal catalog fetched successfully userId={} count={}",
                userId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates an existing meal catalog definition.
     *
     * Airline ownership validation is delegated to the service layer using
     * the authenticated user identifier.
     */
    @PutMapping("/{id}")
    public ResponseEntity<MealResponse> updateMeal(
            @PathVariable Long id,
            @Valid @RequestBody MealRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update meal mealId={} userId={}",
                id,
                userId
        );

        MealResponse response =
                mealService.update(userId, id, request);

        log.info(
                "Meal updated successfully mealId={} userId={}",
                id,
                userId
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Updates only the catalog-level availability status of a meal.
     *
     * This operation allows a meal to be enabled or disabled without
     * replacing the complete meal definition.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<MealResponse> updateMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update meal availability mealId={} available={}",
                id,
                available
        );

        MealResponse response =
                mealService.updateAvailability(id, available);

        log.info(
                "Meal availability updated successfully mealId={} available={}",
                id,
                available
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes a meal definition from the catalog.
     *
     * Referential integrity and deletion eligibility checks are delegated
     * to the service and persistence layers.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeal(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to delete meal mealId={}",
                id
        );

        mealService.delete(id);

        log.info(
                "Meal deleted successfully mealId={}",
                id
        );

        return ResponseEntity.noContent().build();
    }
}