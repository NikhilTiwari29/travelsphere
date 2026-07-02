package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightMealRequest;
import com.nikhil.common_lib.payload.response.FlightMealResponse;
import com.nikhil.services.mapper.FlightMealMapper;
import com.nikhil.services.model.FlightMeal;
import com.nikhil.services.model.Meal;
import com.nikhil.services.repository.FlightMealRepository;
import com.nikhil.services.repository.MealRepository;
import com.nikhil.services.service.FlightMealService;
import com.nikhil.services.specification.FlightMealSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assigns catalog meals to flights with per-route pricing.
 * Consumed by booking-service for meal selection and POST /price/total aggregation.
 * No outbound Feign; validates mealId locally and stores flightId as external ref.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightMealServiceImpl implements FlightMealService {

    private final FlightMealRepository flightMealRepository;
    private final MealRepository mealRepository;

    @Override
    @Transactional
    public FlightMealResponse create(FlightMealRequest request) throws ResourceNotFoundException {
        log.debug("Creating flight meal for flight ID: {} and meal ID: {}",
                request.getFlightId(), request.getMealId());

        Meal meal = mealRepository.findById(request.getMealId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Meal not found with id: " + request.getMealId()));

        Specification<FlightMeal> spec = FlightMealSpecification.hasFlightIdAndMealId(
                request.getFlightId(), request.getMealId());
        if (flightMealRepository.exists(spec)) {
            throw new IllegalArgumentException("Meal is already assigned to this flight");
        }

        FlightMeal flightMeal = FlightMeal.builder()
                .flightId(request.getFlightId())
                .meal(meal)
                .available(request.getAvailable())
                .price(request.getPrice())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        FlightMeal saved = flightMealRepository.save(flightMeal);
        log.info("Flight meal created successfully with id: {}", saved.getId());
        return FlightMealMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public List<FlightMealResponse> bulkCreate(List<FlightMealRequest> requests)
            throws ResourceNotFoundException {
        log.debug("Bulk creating {} flight meals", requests.size());

        List<FlightMealResponse> responses = new ArrayList<>();

        for (FlightMealRequest request : requests) {
            Meal meal = mealRepository.findById(request.getMealId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Meal not found with id: " + request.getMealId()));

            Specification<FlightMeal> spec = FlightMealSpecification.hasFlightIdAndMealId(
                    request.getFlightId(), request.getMealId());
            if (flightMealRepository.exists(spec)) {
                log.warn("Skipping flight meal - meal {} already assigned to flight {}",
                        request.getMealId(), request.getFlightId());
                continue;
            }

            FlightMeal flightMeal = FlightMeal.builder()
                    .flightId(request.getFlightId())
                    .meal(meal)
                    .available(request.getAvailable())
                    .price(request.getPrice())
                    .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                    .build();

            FlightMeal saved = flightMealRepository.save(flightMeal);
            responses.add(FlightMealMapper.toResponse(saved));
        }

        log.info("Successfully created {} flight meals", responses.size());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public FlightMealResponse getById(Long id) throws ResourceNotFoundException {
        FlightMeal flightMeal = flightMealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightMeal not found with id: " + id));
        return FlightMealMapper.toResponse(flightMeal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightMealResponse> getByFlightId(Long flightId) {
        Specification<FlightMeal> spec = FlightMealSpecification.hasFlightId(flightId);
        return flightMealRepository.findAll(spec).stream()
                .map(FlightMealMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlightMealResponse> getAllByIds(List<Long> Ids) {
        List<FlightMeal> meals = flightMealRepository.findAllById(Ids);
        return meals.stream().map(
                FlightMealMapper::toResponse
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FlightMealResponse update(Long id, FlightMealRequest request) throws ResourceNotFoundException {
        log.debug("Updating flight meal with id: {}", id);

        FlightMeal flightMeal = flightMealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightMeal not found with id: " + id));

        // Update flight ID if changed
        if (!flightMeal.getFlightId().equals(request.getFlightId())) {
            flightMeal.setFlightId(request.getFlightId());
        }

        // Update meal if changed
        if (!flightMeal.getMeal().getId().equals(request.getMealId())) {
            Meal meal = mealRepository.findById(request.getMealId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Meal not found with id: " + request.getMealId()));
            flightMeal.setMeal(meal);
        }

        flightMeal.setAvailable(request.getAvailable());
        flightMeal.setPrice(request.getPrice());
        flightMeal.setDisplayOrder(request.getDisplayOrder());

        FlightMeal updated = flightMealRepository.save(flightMeal);
        log.info("Flight meal updated successfully with id: {}", updated.getId());
        return FlightMealMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) throws ResourceNotFoundException {
        if (!flightMealRepository.existsById(id)) {
            throw new ResourceNotFoundException("FlightMeal not found with id: " + id);
        }
        flightMealRepository.deleteById(id);
        log.info("Flight meal deleted successfully with id: {}", id);
    }

    @Override
    @Transactional
    public FlightMealResponse updateAvailability(Long id, Boolean available) throws ResourceNotFoundException {
        FlightMeal flightMeal = flightMealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightMeal not found with id: " + id));
        flightMeal.setAvailable(available);
        FlightMeal updated = flightMealRepository.save(flightMeal);
        log.info("Flight meal availability updated successfully for id: {}", updated.getId());
        return FlightMealMapper.toResponse(updated);
    }

    @Override
    public Double calculateMealPrice(List<Long> mealIds) {
        List<FlightMeal> meals=flightMealRepository.findAllById(mealIds);
        double total=0.0;
        for(FlightMeal flightMeal : meals) {
            total+=flightMeal.getPrice();
        }
        return total;
    }


}
