package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.MealRequest;
import com.nikhil.common_lib.payload.response.MealResponse;
import com.nikhil.services.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @PostMapping
    public ResponseEntity<MealResponse> createMeal(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody MealRequest request) throws ResourceNotFoundException {
        MealResponse response = mealService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<MealResponse>> bulkCreateMeals(
            @Valid @RequestBody List<MealRequest> requests,
            @RequestHeader("X-User-Id") Long userId) throws ResourceNotFoundException {
        List<MealResponse> responses = mealService.bulkCreate(userId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<MealResponse> getMealById(@PathVariable Long id)
            throws ResourceNotFoundException {
        return ResponseEntity.ok(mealService.getById(id));
    }

    @GetMapping("/airline")
    public ResponseEntity<List<MealResponse>> getMealsByAirlineId(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(mealService.getByAirlineId(userId));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<MealResponse> updateMeal(
            @PathVariable Long id,
            @Valid @RequestBody MealRequest request,
            @RequestHeader("X-User-Id") Long userId) throws ResourceNotFoundException {
        return ResponseEntity.ok(mealService.update(userId, id, request));
    }

    @PatchMapping("/{id:\\d+}/availability")
    public ResponseEntity<MealResponse> updateMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available) throws ResourceNotFoundException {
        return ResponseEntity.ok(mealService.updateAvailability(id, available));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteMeal(@PathVariable Long id) throws ResourceNotFoundException {
        mealService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
