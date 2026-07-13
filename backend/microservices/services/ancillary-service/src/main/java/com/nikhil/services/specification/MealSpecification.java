package com.nikhil.services.specification;

import com.nikhil.services.model.Meal;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides reusable JPA Specifications for dynamically querying Meal entities.
 *
 * Specifications can be composed to support airline-scoped filtering by
 * meal code, meal type, availability, dietary restriction, and free-text
 * search criteria.
 *
 * Blank or null filter values are treated as non-restrictive predicates,
 * allowing callers to compose optional search criteria without branching
 * in the service or repository layer.
 */
@Slf4j
public final class MealSpecification {

    private MealSpecification() {
        // Utility class; prevent instantiation.
    }


    /**
     * Filters meals by their catalog code.
     */
    public static Specification<Meal> hasCode(String code) {

        return (root, query, criteriaBuilder) -> {

            if (code == null || code.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("code"),
                    code
            );
        };
    }


    /**
     * Restricts results to meals owned by a specific airline.
     *
     * airlineId is a cross-service reference and is stored directly on the
     * Meal entity rather than represented as a JPA association.
     */
    public static Specification<Meal> hasAirlineId(Long airlineId) {

        return (root, query, criteriaBuilder) -> {

            if (airlineId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("airlineId"),
                    airlineId
            );
        };
    }


    /**
     * Filters meals by their catalog-level meal type.
     */
    public static Specification<Meal> hasMealType(String mealType) {

        return (root, query, criteriaBuilder) -> {

            if (mealType == null || mealType.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("mealType"),
                    mealType
            );
        };
    }


    /**
     * Filters meals by their catalog-level availability status.
     *
     * A null value disables the filter and allows both available and
     * unavailable catalog entries to be returned.
     */
    public static Specification<Meal> isAvailable(Boolean available) {

        return (root, query, criteriaBuilder) -> {

            if (available == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("available"),
                    available
            );
        };
    }


    /**
     * Filters meals by dietary restriction classification.
     */
    public static Specification<Meal> hasDietaryRestriction(
            String dietaryRestriction
    ) {

        return (root, query, criteriaBuilder) -> {

            if (dietaryRestriction == null
                    || dietaryRestriction.isBlank()) {

                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("dietaryRestriction"),
                    dietaryRestriction
            );
        };
    }


    /**
     * Performs a case-insensitive keyword search across meal name
     * and ingredient information.
     *
     * The keyword is matched using contains semantics.
     */
    public static Specification<Meal> searchByKeyword(
            String keyword
    ) {

        return (root, query, criteriaBuilder) -> {

            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String likePattern =
                    "%" + keyword.trim().toLowerCase() + "%";

            return criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("name")),
                            likePattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("ingredients")),
                            likePattern
                    )
            );
        };
    }


    /**
     * Matches a meal by its airline-scoped business key.
     *
     * The combination of meal code and airline identifier is used to detect
     * duplicate catalog entries within an airline while allowing different
     * airlines to use the same meal code.
     */
    public static Specification<Meal> hasCodeAndAirlineId(
            String code,
            Long airlineId
    ) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            if (code != null && !code.isBlank()) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("code"),
                                code
                        )
                );
            }

            if (airlineId != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("airlineId"),
                                airlineId
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }


    /**
     * Applies deterministic catalog ordering.
     *
     * Meals are ordered first by configured display order and then by name
     * to provide stable ordering when multiple records share the same
     * display-order value.
     */
    public static Specification<Meal> orderByDisplayOrder() {

        return (root, query, criteriaBuilder) -> {

            if (query != null) {

                query.orderBy(
                        criteriaBuilder.asc(
                                root.get("displayOrder")
                        ),
                        criteriaBuilder.asc(
                                root.get("name")
                        )
                );
            }

            return criteriaBuilder.conjunction();
        };
    }


    /**
     * Builds the composite specification used for dynamic meal catalog search.
     *
     * Only non-null and non-blank filter values contribute restrictive
     * predicates to the generated query.
     */
    public static Specification<Meal> buildSpecification(
            Long airlineId,
            String mealType,
            Boolean available,
            String dietaryRestriction,
            String keyword
    ) {

        log.debug(
                "Building meal search specification airlineId={} mealType={} available={} dietaryRestriction={} keywordPresent={}",
                airlineId,
                mealType,
                available,
                dietaryRestriction,
                keyword != null && !keyword.isBlank()
        );

        return Specification
                .where(hasAirlineId(airlineId))
                .and(hasMealType(mealType))
                .and(isAvailable(available))
                .and(hasDietaryRestriction(dietaryRestriction))
                .and(searchByKeyword(keyword));
    }
}