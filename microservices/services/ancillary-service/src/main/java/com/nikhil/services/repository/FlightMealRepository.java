package com.nikhil.services.repository;

import com.nikhil.services.model.FlightMeal;
import com.nikhil.services.model.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FlightMealRepository extends JpaRepository<FlightMeal, Long>, JpaSpecificationExecutor<FlightMeal> {

    Optional<FlightMeal> findByFlightIdAndMeal(Long flightId, Meal meal);

    void deleteByFlightId(Long flightId);

    /**
     * Checks whether the specified catalog Meal is already assigned
     * to the given Flight.
     */
    boolean existsByFlightIdAndMeal_Id(
            Long flightId,
            Long mealId
    );

    /**
     * Retrieves all meal offerings configured for a Flight.
     */
    List<FlightMeal> findByFlightId(Long flightId);
}
