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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation for managing flight-specific meal offerings.
 *
 * A FlightMeal associates a catalog Meal with a Flight and stores
 * flight-specific commercial attributes such as price, availability,
 * and display order.
 *
 * Domain relationship:
 *
 * Meal Catalog
 *      ↓
 * FlightMeal
 *      ↓
 * Flight
 *
 * Main responsibilities:
 *   - Assign catalog meals to flights
 *   - Prevent duplicate meal assignments for the same flight
 *   - Support bulk flight-meal configuration
 *   - Manage flight-specific meal pricing and availability
 *   - Retrieve meal offerings for booking flows
 *   - Calculate aggregate meal charges during checkout
 *
 * Cross-service boundary:
 *   - mealId references the local Meal entity.
 *   - flightId is stored as an external service reference.
 *   - No JPA relationship is created for Flight because the Flight domain
 *     is owned by flight-ops-service.
 *
 * Transaction strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, bulk create, update, availability update, and delete operations
 *     explicitly use writable transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightMealServiceImpl implements FlightMealService {

    private final FlightMealRepository flightMealRepository;
    private final MealRepository mealRepository;


    /**
     * Assigns a catalog meal to a flight.
     *
     * The operation validates the local Meal reference and prevents duplicate
     * FlightMeal assignments for the same flight and meal combination.
     */
    @Override
    @Transactional
    public FlightMealResponse create(
            FlightMealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating flight meal flightId={} mealId={}",
                request.getFlightId(),
                request.getMealId()
        );

        /*
         * Validate that the requested catalog Meal exists before creating
         * the flight-specific offering.
         */
        Meal meal = mealRepository.findById(request.getMealId())
                .orElseThrow(() -> {

                    log.warn(
                            "Cannot create flight meal because meal was not found mealId={} flightId={}",
                            request.getMealId(),
                            request.getFlightId()
                    );

                    return new ResourceNotFoundException(
                            "Meal not found with id: " + request.getMealId()
                    );
                });

        /*
         * Enforce uniqueness of the Flight-Meal assignment.
         *
         * A catalog Meal may be offered on multiple flights, and a Flight may
         * offer multiple meals. However, the same Meal must not be assigned
         * more than once to the same Flight.
         *
         * Examples:
         *   Flight 1 + Meal 1 -> allowed
         *   Flight 1 + Meal 2 -> allowed
         *   Flight 2 + Meal 1 -> allowed
         *   Flight 1 + Meal 1 -> duplicate, rejected
         */
        if (flightMealRepository.existsByFlightIdAndMeal_Id(
                request.getFlightId(),
                request.getMealId()
        )) {

            log.warn(
                    "Cannot create flight meal because assignment already exists flightId={} mealId={}",
                    request.getFlightId(),
                    request.getMealId()
            );

            throw new IllegalArgumentException(
                    "Meal is already assigned to this flight"
            );
        }

        FlightMeal flightMeal = FlightMeal.builder()
                .flightId(request.getFlightId())
                .meal(meal)
                .available(request.getAvailable())
                .price(request.getPrice())
                .displayOrder(
                        request.getDisplayOrder() != null
                                ? request.getDisplayOrder()
                                : 0
                )
                .build();

        FlightMeal saved =
                flightMealRepository.save(flightMeal);

        log.info(
                "Flight meal created successfully flightMealId={} flightId={} mealId={}",
                saved.getId(),
                saved.getFlightId(),
                saved.getMeal().getId()
        );

        return FlightMealMapper.toResponse(saved);
    }


    /**
     * Assigns multiple catalog meals to flights in one transaction.
     *
     * Existing Flight-Meal assignments are skipped while eligible records
     * are created. Any validation or persistence failure rolls back the
     * complete bulk operation.
     */
    @Override
    @Transactional
    public List<FlightMealResponse> bulkCreate(
            List<FlightMealRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Starting bulk flight meal creation requestedCount={}",
                requests.size()
        );

        List<FlightMealResponse> responses =
                new ArrayList<>();

        int skippedCount = 0;

        for (FlightMealRequest request : requests) {

            /*
             * Validate the referenced catalog Meal before creating
             * the FlightMeal association.
             */
            Meal meal = mealRepository.findById(request.getMealId())
                    .orElseThrow(() -> {

                        log.warn(
                                "Bulk flight meal creation failed because meal was not found mealId={} flightId={}",
                                request.getMealId(),
                                request.getFlightId()
                        );

                        return new ResourceNotFoundException(
                                "Meal not found with id: "
                                        + request.getMealId()
                        );
                    });

            /*
             * Skip an existing Flight-Meal assignment so the remaining valid
             * records in the bulk request can still be processed.
             */
            if (flightMealRepository.existsByFlightIdAndMeal_Id(
                    request.getFlightId(),
                    request.getMealId()
            )) {

                skippedCount++;

                log.warn(
                        "Skipping existing flight meal assignment flightId={} mealId={}",
                        request.getFlightId(),
                        request.getMealId()
                );

                continue;
            }

            FlightMeal flightMeal = FlightMeal.builder()
                    .flightId(request.getFlightId())
                    .meal(meal)
                    .available(request.getAvailable())
                    .price(request.getPrice())
                    .displayOrder(
                            request.getDisplayOrder() != null
                                    ? request.getDisplayOrder()
                                    : 0
                    )
                    .build();

            FlightMeal saved =
                    flightMealRepository.save(flightMeal);

            responses.add(
                    FlightMealMapper.toResponse(saved)
            );
        }

        log.info(
                "Bulk flight meal creation completed requestedCount={} createdCount={} skippedCount={}",
                requests.size(),
                responses.size(),
                skippedCount
        );

        return responses;
    }


    /**
     * Retrieves a FlightMeal by its unique identifier.
     */
    @Override
    public FlightMealResponse getById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching flight meal flightMealId={}",
                id
        );

        FlightMeal flightMeal =
                flightMealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight meal not found flightMealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightMeal not found with id: " + id
                            );
                        });

        log.debug(
                "Flight meal fetched successfully flightMealId={} flightId={} mealId={}",
                flightMeal.getId(),
                flightMeal.getFlightId(),
                flightMeal.getMeal().getId()
        );

        return FlightMealMapper.toResponse(flightMeal);
    }


    /**
     * Retrieves all meal offerings configured for a flight.
     *
     * Used by booking flows to present the meal products available for
     * the selected flight.
     */
    @Override
    public List<FlightMealResponse> getByFlightId(
            Long flightId
    ) {

        log.debug(
                "Fetching flight meals flightId={}",
                flightId
        );

        List<FlightMealResponse> responses =
                flightMealRepository.findByFlightId(flightId)
                        .stream()
                        .map(FlightMealMapper::toResponse)
                        .toList();

        log.debug(
                "Flight meals fetched successfully flightId={} count={}",
                flightId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves multiple FlightMeal records by their identifiers.
     *
     * This operation supports downstream services that need to resolve
     * multiple selected meal line items in a single database operation.
     */
    @Override
    public List<FlightMealResponse> getAllByIds(
            List<Long> ids
    ) {

        log.debug(
                "Fetching flight meals by IDs requestedCount={}",
                ids.size()
        );

        List<FlightMeal> flightMeals =
                flightMealRepository.findAllById(ids);

        List<FlightMealResponse> responses =
                flightMeals.stream()
                        .map(FlightMealMapper::toResponse)
                        .toList();

        log.debug(
                "Flight meals fetched by IDs requestedCount={} resultCount={}",
                ids.size(),
                responses.size()
        );

        return responses;
    }


    /**
     * Updates an existing FlightMeal configuration.
     *
     * The Meal reference is reloaded only when mealId changes.
     * The Flight reference is stored as an external identifier and can
     * therefore be updated without loading a local Flight entity.
     */
    @Override
    @Transactional
    public FlightMealResponse update(
            Long id,
            FlightMealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating flight meal flightMealId={} requestedFlightId={} requestedMealId={}",
                id,
                request.getFlightId(),
                request.getMealId()
        );

        FlightMeal flightMeal =
                flightMealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update flight meal because record was not found flightMealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightMeal not found with id: " + id
                            );
                        });

        /*
         * Before changing the Flight or Meal association, verify that the
         * requested combination is not already assigned to another record.
         */
        boolean assignmentChanged =
                !flightMeal.getFlightId().equals(request.getFlightId())
                        || !flightMeal.getMeal().getId().equals(request.getMealId());

        if (assignmentChanged
                && flightMealRepository.existsByFlightIdAndMeal_Id(
                request.getFlightId(),
                request.getMealId()
        )) {

            log.warn(
                    "Cannot update flight meal because target assignment already exists flightMealId={} flightId={} mealId={}",
                    id,
                    request.getFlightId(),
                    request.getMealId()
            );

            throw new IllegalArgumentException(
                    "Meal is already assigned to this flight"
            );
        }

        /*
         * Update the external Flight reference only when its value changes.
         */
        if (!flightMeal.getFlightId().equals(request.getFlightId())) {

            log.debug(
                    "Changing flight reference flightMealId={} oldFlightId={} newFlightId={}",
                    id,
                    flightMeal.getFlightId(),
                    request.getFlightId()
            );

            flightMeal.setFlightId(
                    request.getFlightId()
            );
        }

        /*
         * Reload the local Meal entity only when the Meal assignment changes.
         */
        if (!flightMeal.getMeal().getId().equals(request.getMealId())) {

            Meal meal =
                    mealRepository.findById(request.getMealId())
                            .orElseThrow(() -> {

                                log.warn(
                                        "Cannot update flight meal because requested meal was not found flightMealId={} mealId={}",
                                        id,
                                        request.getMealId()
                                );

                                return new ResourceNotFoundException(
                                        "Meal not found with id: "
                                                + request.getMealId()
                                );
                            });

            flightMeal.setMeal(meal);
        }

        flightMeal.setAvailable(request.getAvailable());
        flightMeal.setPrice(request.getPrice());
        flightMeal.setDisplayOrder(
                request.getDisplayOrder() != null
                        ? request.getDisplayOrder()
                        : 0
        );

        FlightMeal updated =
                flightMealRepository.save(flightMeal);

        log.info(
                "Flight meal updated successfully flightMealId={} flightId={} mealId={}",
                updated.getId(),
                updated.getFlightId(),
                updated.getMeal().getId()
        );

        return FlightMealMapper.toResponse(updated);
    }


    /**
     * Deletes a FlightMeal assignment.
     *
     * The entity is loaded before deletion so a missing identifier produces
     * a consistent domain-level not-found response.
     */
    @Override
    @Transactional
    public void delete(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Deleting flight meal flightMealId={}",
                id
        );

        FlightMeal flightMeal =
                flightMealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete flight meal because record was not found flightMealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightMeal not found with id: " + id
                            );
                        });

        flightMealRepository.delete(flightMeal);

        log.info(
                "Flight meal deleted successfully flightMealId={} flightId={} mealId={}",
                id,
                flightMeal.getFlightId(),
                flightMeal.getMeal().getId()
        );
    }


    /**
     * Updates only the availability state of a FlightMeal.
     *
     * This operation supports temporary activation or deactivation of a meal
     * offering without replacing its pricing or assignment configuration.
     */
    @Override
    @Transactional
    public FlightMealResponse updateAvailability(
            Long id,
            Boolean available
    ) throws ResourceNotFoundException {

        log.info(
                "Updating flight meal availability flightMealId={} available={}",
                id,
                available
        );

        FlightMeal flightMeal =
                flightMealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update flight meal availability because record was not found flightMealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightMeal not found with id: " + id
                            );
                        });

        flightMeal.setAvailable(available);

        FlightMeal updated =
                flightMealRepository.save(flightMeal);

        log.info(
                "Flight meal availability updated successfully flightMealId={} available={}",
                updated.getId(),
                updated.getAvailable()
        );

        return FlightMealMapper.toResponse(updated);
    }


    /**
     * Calculates the aggregate price of selected FlightMeal records.
     *
     * The input identifiers represent FlightMeal records rather than catalog
     * Meal records because pricing is configured at the flight-meal level.
     *
     * Used by booking-service during checkout price calculation.
     */
    @Override
    public Double calculateMealPrice(
            List<Long> flightMealIds
    ) {

        log.debug(
                "Calculating meal price total requestedCount={}",
                flightMealIds.size()
        );

        List<FlightMeal> flightMeals =
                flightMealRepository.findAllById(flightMealIds);

        double totalPrice =
                flightMeals.stream()
                        .mapToDouble(FlightMeal::getPrice)
                        .sum();

        log.debug(
                "Meal price calculation completed requestedCount={} resolvedCount={} totalPrice={}",
                flightMealIds.size(),
                flightMeals.size(),
                totalPrice
        );

        return totalPrice;
    }
}