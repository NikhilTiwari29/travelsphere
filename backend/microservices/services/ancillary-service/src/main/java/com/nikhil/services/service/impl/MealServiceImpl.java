package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.MealRequest;
import com.nikhil.common_lib.payload.response.MealResponse;
import com.nikhil.services.Integration.AirlineIntegrationService;
import com.nikhil.services.mapper.MealMapper;
import com.nikhil.services.model.Meal;
import com.nikhil.services.repository.MealRepository;
import com.nikhil.services.service.MealService;
import com.nikhil.services.specification.MealSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation for managing airline-owned meal catalog entries.
 *
 * Meal represents a reusable catalog definition. Flight-specific pricing
 * and availability are managed separately through the FlightMeal domain.
 *
 * Ownership model:
 *
 * User
 *   ↓
 * AirlineIntegrationService
 *   ↓
 * Airline Core Service
 *   ↓
 * Airline ID
 *   ↓
 * Meal Catalog
 *
 * Main responsibilities:
 *   - Resolve airline ownership from the authenticated user.
 *   - Create and maintain airline meal catalog entries.
 *   - Enforce meal-code uniqueness within an airline.
 *   - Support bulk catalog creation.
 *   - Manage catalog-level availability.
 *
 * Cross-service boundary:
 *   - Airline ownership is resolved through AirlineIntegrationService.
 *   - The local database stores airlineId as a cross-service reference.
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
public class MealServiceImpl implements MealService {

    private final MealRepository mealRepository;
    private final AirlineIntegrationService airlineIntegrationService;


    /**
     * Creates a meal in the catalog owned by the authenticated user's airline.
     *
     * Meal codes are unique within an airline catalog.
     */
    @Override
    @Transactional
    public MealResponse create(
            Long userId,
            MealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating meal userId={} mealCode={}",
                userId,
                request.getCode()
        );

        /*
         * Resolve airline ownership from the authenticated user rather than
         * accepting airlineId from the client request.
         */
        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        log.debug(
                "Resolved airline for meal creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Enforce meal-code uniqueness within the airline catalog.
         */
        Specification<Meal> specification =
                MealSpecification.hasCodeAndAirlineId(
                        request.getCode(),
                        airlineId
                );

        if (mealRepository.exists(specification)) {

            log.warn(
                    "Cannot create meal because code already exists airlineId={} mealCode={}",
                    airlineId,
                    request.getCode()
            );

            throw new IllegalArgumentException(
                    "Meal with code "
                            + request.getCode()
                            + " already exists for this airline"
            );
        }

        Meal meal = Meal.builder()
                .code(request.getCode())
                .name(request.getName())
                .mealType(request.getMealType())
                .dietaryRestriction(request.getDietaryRestriction())
                .ingredients(request.getIngredients())
                .imageUrl(request.getImageUrl())
                .available(request.getAvailable())
                .requiresAdvanceBooking(
                        request.getRequiresAdvanceBooking() != null
                                ? request.getRequiresAdvanceBooking()
                                : false
                )
                .advanceBookingHours(request.getAdvanceBookingHours())
                .displayOrder(
                        request.getDisplayOrder() != null
                                ? request.getDisplayOrder()
                                : 0
                )
                .airlineId(airlineId)
                .build();

        Meal savedMeal =
                mealRepository.save(meal);

        log.info(
                "Meal created successfully mealId={} airlineId={} mealCode={}",
                savedMeal.getId(),
                airlineId,
                savedMeal.getCode()
        );

        return MealMapper.toResponse(savedMeal);
    }


    /**
     * Creates multiple meal catalog entries for one airline.
     *
     * Airline ownership is resolved once for the complete request. Existing
     * meal codes are skipped while eligible records are created.
     *
     * The operation executes within one transaction. Any unhandled validation
     * or persistence failure rolls back writes performed by the batch.
     */
    @Override
    @Transactional
    public List<MealResponse> bulkCreate(
            Long userId,
            List<MealRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Starting bulk meal creation userId={} requestedCount={}",
                userId,
                requests.size()
        );

        /*
         * Resolve the airline once and reuse the identifier for every meal
         * created by this batch.
         */
        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        log.debug(
                "Resolved airline for bulk meal creation userId={} airlineId={}",
                userId,
                airlineId
        );

        List<MealResponse> responses =
                new ArrayList<>();

        int skippedCount = 0;

        for (MealRequest request : requests) {

            Specification<Meal> specification =
                    MealSpecification.hasCodeAndAirlineId(
                            request.getCode(),
                            airlineId
                    );

            /*
             * Existing catalog entries are intentionally skipped so the
             * remaining eligible records can still be processed.
             */
            if (mealRepository.exists(specification)) {

                skippedCount++;

                log.warn(
                        "Skipping existing meal during bulk creation airlineId={} mealCode={}",
                        airlineId,
                        request.getCode()
                );

                continue;
            }

            Meal meal = Meal.builder()
                    .code(request.getCode())
                    .name(request.getName())
                    .mealType(request.getMealType())
                    .dietaryRestriction(request.getDietaryRestriction())
                    .ingredients(request.getIngredients())
                    .imageUrl(request.getImageUrl())
                    .available(request.getAvailable())
                    .requiresAdvanceBooking(
                            request.getRequiresAdvanceBooking() != null
                                    ? request.getRequiresAdvanceBooking()
                                    : false
                    )
                    .advanceBookingHours(request.getAdvanceBookingHours())
                    .displayOrder(
                            request.getDisplayOrder() != null
                                    ? request.getDisplayOrder()
                                    : 0
                    )
                    .airlineId(airlineId)
                    .build();

            Meal savedMeal =
                    mealRepository.save(meal);

            responses.add(
                    MealMapper.toResponse(savedMeal)
            );
        }

        log.info(
                "Bulk meal creation completed airlineId={} requestedCount={} createdCount={} skippedCount={}",
                airlineId,
                requests.size(),
                responses.size(),
                skippedCount
        );

        return responses;
    }


    /**
     * Retrieves a meal catalog entry by its unique identifier.
     */
    @Override
    public MealResponse getById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching meal mealId={}",
                id
        );

        Meal meal =
                mealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Meal not found mealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Meal not found with id: " + id
                            );
                        });

        log.debug(
                "Meal fetched successfully mealId={} airlineId={}",
                meal.getId(),
                meal.getAirlineId()
        );

        return MealMapper.toResponse(meal);
    }


    /**
     * Retrieves all meal catalog entries owned by the authenticated
     * user's airline.
     */
    @Override
    public List<MealResponse> getByAirlineId(
            Long userId
    ) {

        log.debug(
                "Fetching airline meal catalog userId={}",
                userId
        );

        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        Specification<Meal> specification =
                MealSpecification.hasAirlineId(airlineId);

        List<MealResponse> responses =
                mealRepository.findAll(specification)
                        .stream()
                        .map(MealMapper::toResponse)
                        .toList();

        log.debug(
                "Airline meal catalog fetched successfully userId={} airlineId={} count={}",
                userId,
                airlineId,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates a meal catalog entry.
     *
     * When the meal code changes, uniqueness is revalidated within the
     * authenticated user's airline catalog.
     */
    @Override
    @Transactional
    public MealResponse update(
            Long userId,
            Long id,
            MealRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating meal mealId={} userId={} requestedCode={}",
                id,
                userId,
                request.getCode()
        );

        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        Meal meal =
                mealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update meal because record was not found mealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Meal not found with id: " + id
                            );
                        });

        /*
         * Revalidate code uniqueness only when the business key changes.
         */
        if (!meal.getCode().equals(request.getCode())) {

            Specification<Meal> specification =
                    MealSpecification.hasCodeAndAirlineId(
                            request.getCode(),
                            airlineId
                    );

            if (mealRepository.exists(specification)) {

                log.warn(
                        "Cannot update meal because code already exists mealId={} airlineId={} requestedCode={}",
                        id,
                        airlineId,
                        request.getCode()
                );

                throw new IllegalArgumentException(
                        "Meal with code "
                                + request.getCode()
                                + " already exists for this airline"
                );
            }
        }

        meal.setCode(request.getCode());
        meal.setName(request.getName());
        meal.setMealType(request.getMealType());
        meal.setDietaryRestriction(request.getDietaryRestriction());
        meal.setIngredients(request.getIngredients());
        meal.setImageUrl(request.getImageUrl());
        meal.setAvailable(request.getAvailable());
        meal.setRequiresAdvanceBooking(
                request.getRequiresAdvanceBooking()
        );
        meal.setAdvanceBookingHours(
                request.getAdvanceBookingHours()
        );
        meal.setDisplayOrder(
                request.getDisplayOrder()
        );

        /*
         * Explicit save is retained for command-operation clarity. Since the
         * entity is managed inside a writable transaction, JPA dirty checking
         * would also persist these changes at transaction commit.
         */
        Meal updatedMeal =
                mealRepository.save(meal);

        log.info(
                "Meal updated successfully mealId={} airlineId={} mealCode={}",
                updatedMeal.getId(),
                updatedMeal.getAirlineId(),
                updatedMeal.getCode()
        );

        return MealMapper.toResponse(updatedMeal);
    }


    /**
     * Deletes a meal catalog entry.
     *
     * The entity is loaded before deletion to provide consistent not-found
     * handling and allow persistence-layer referential integrity rules to
     * control deletion of referenced meals.
     */
    @Override
    @Transactional
    public void delete(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Deleting meal mealId={}",
                id
        );

        Meal meal =
                mealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete meal because record was not found mealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Meal not found with id: " + id
                            );
                        });

        mealRepository.delete(meal);

        log.info(
                "Meal deleted successfully mealId={} airlineId={} mealCode={}",
                meal.getId(),
                meal.getAirlineId(),
                meal.getCode()
        );
    }


    /**
     * Updates only the catalog-level availability state of a meal.
     *
     * This operation enables or disables a meal without replacing the
     * remaining catalog configuration.
     */
    @Override
    @Transactional
    public MealResponse updateAvailability(
            Long id,
            Boolean available
    ) throws ResourceNotFoundException {

        log.info(
                "Updating meal availability mealId={} available={}",
                id,
                available
        );

        Meal meal =
                mealRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update meal availability because record was not found mealId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Meal not found with id: " + id
                            );
                        });

        meal.setAvailable(available);

        /*
         * Explicit save is retained for consistency with command operations.
         * Dirty checking would also persist the change at transaction commit.
         */
        Meal updatedMeal =
                mealRepository.save(meal);

        log.info(
                "Meal availability updated successfully mealId={} available={}",
                updatedMeal.getId(),
                updatedMeal.getAvailable()
        );

        return MealMapper.toResponse(updatedMeal);
    }
}