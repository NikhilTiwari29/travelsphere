package com.nikhil.services.specification;

import com.nikhil.services.model.FlightMeal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides reusable dynamic query conditions for the FlightMeal entity.
 *
 * A Spring Data JPA Specification represents a condition that can be added
 * to a database query dynamically.
 *
 * Instead of creating separate repository methods such as:
 *
 *   findByFlightId(...)
 *   findByMealId(...)
 *   findByAvailable(...)
 *   findByFlightIdAndAvailable(...)
 *   findByFlightIdAndMealIdAndAvailable(...)
 *
 * we define small reusable Specifications and combine them when required.
 *
 * Example:
 *
 *   hasFlightId(1L)
 *       .and(isAvailable(true))
 *
 * Conceptually produces:
 *
 *   WHERE flight_id = 1
 *     AND available = true
 *
 * This approach is useful when API filters are optional and different
 * combinations of filters can be supplied by the client.
 */
public class FlightMealSpecification {


    /**
     * Creates a condition that filters FlightMeal records by flight ID.
     *
     * Example:
     *
     *   hasFlightId(10L)
     *
     * Conceptually produces:
     *
     *   WHERE flight_id = 10
     *
     * In the Specification lambda:
     *
     *   root
     *     Represents the FlightMeal entity being queried.
     *
     *   query
     *     Represents the complete JPA query being constructed.
     *
     *   criteriaBuilder
     *     Provides methods for creating SQL-like conditions such as
     *     equal, greaterThan, like, and, or, and ordering.
     *
     * If flightId is null, conjunction() returns an always-true condition.
     * This means the specification does not restrict the query.
     */
    public static Specification<FlightMeal> hasFlightId(Long flightId) {

        return (root, query, criteriaBuilder) -> {

            /*
             * No flight filter was supplied.
             *
             * conjunction() behaves like:
             *
             *   WHERE 1 = 1
             *
             * Therefore, this specification adds no effective filtering
             * and can safely be combined with other specifications.
             */
            if (flightId == null) {
                return criteriaBuilder.conjunction();
            }

            /*
             * root.get("flightId") refers to the flightId field of
             * the FlightMeal entity.
             *
             * Conceptually:
             *
             *   WHERE flight_id = :flightId
             */
            return criteriaBuilder.equal(
                    root.get("flightId"),
                    flightId
            );
        };
    }


    /**
     * Creates a condition that filters FlightMeal records by Meal ID.
     *
     * FlightMeal contains a relationship to Meal rather than storing mealId
     * as a simple field.
     *
     * For example, if the entity contains:
     *
     *   @ManyToOne
     *   private Meal meal;
     *
     * then:
     *
     *   root.get("meal")
     *
     * navigates from FlightMeal to the associated Meal entity, and:
     *
     *   root.get("meal").get("id")
     *
     * accesses the ID of that Meal.
     *
     * Conceptually:
     *
     *   WHERE meal_id = :mealId
     */
    public static Specification<FlightMeal> hasMealId(Long mealId) {

        return (root, query, criteriaBuilder) -> {

            /*
             * If no meal ID is supplied, do not restrict the query
             * by Meal.
             */
            if (mealId == null) {
                return criteriaBuilder.conjunction();
            }

            /*
             * Navigate through the FlightMeal -> Meal relationship
             * and compare the Meal primary key.
             */
            return criteriaBuilder.equal(
                    root.get("meal").get("id"),
                    mealId
            );
        };
    }


    /**
     * Creates a condition that filters FlightMeal records by availability.
     *
     * Examples:
     *
     *   isAvailable(true)
     *
     * Conceptually produces:
     *
     *   WHERE available = true
     *
     * while:
     *
     *   isAvailable(null)
     *
     * adds no availability restriction.
     */
    public static Specification<FlightMeal> isAvailable(
            Boolean available
    ) {

        return (root, query, criteriaBuilder) -> {

            /*
             * A null value means that the caller wants both available
             * and unavailable FlightMeal records.
             */
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
     * Creates a combined condition for a specific Flight and Meal.
     *
     * This specification is primarily used to check whether the same
     * catalog Meal has already been assigned to a Flight.
     *
     * Business rule:
     *
     *   A Flight can offer many Meals.
     *   A Meal can be offered on many Flights.
     *
     * However, the same Meal should not be assigned more than once
     * to the same Flight.
     *
     * Examples:
     *
     *   Flight 1 + Meal 1 -> allowed
     *   Flight 1 + Meal 2 -> allowed
     *   Flight 2 + Meal 1 -> allowed
     *   Flight 1 + Meal 1 -> duplicate
     *
     * When both values are supplied, the generated condition is
     * conceptually:
     *
     *   WHERE flight_id = :flightId
     *     AND meal_id = :mealId
     */
    public static Specification<FlightMeal> hasFlightIdAndMealId(
            Long flightId,
            Long mealId
    ) {

        return (root, query, criteriaBuilder) -> {

            /*
             * Each Predicate represents one WHERE condition.
             *
             * For example:
             *
             *   flight_id = 1
             *
             * is one Predicate, and:
             *
             *   meal_id = 5
             *
             * is another Predicate.
             */
            List<Predicate> predicates =
                    new ArrayList<>();

            /*
             * Add the Flight condition only when flightId is provided.
             */
            if (flightId != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("flightId"),
                                flightId
                        )
                );
            }

            /*
             * Add the Meal condition only when mealId is provided.
             *
             * Because Meal is an entity relationship, we navigate
             * through the meal field to its id field.
             */
            if (mealId != null) {

                predicates.add(
                        criteriaBuilder.equal(
                                root.get("meal").get("id"),
                                mealId
                        )
                );
            }

            /*
             * Combine all collected conditions using AND.
             *
             * For example:
             *
             *   flight_id = 1
             *   meal_id = 5
             *
             * becomes:
             *
             *   WHERE flight_id = 1
             *     AND meal_id = 5
             */
            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }


    /**
     * Applies deterministic ordering to FlightMeal query results.
     *
     * Results are sorted:
     *
     *   1. By displayOrder in ascending order.
     *   2. By Meal name in ascending alphabetical order when multiple
     *      records have the same displayOrder.
     *
     * Conceptually:
     *
     *   ORDER BY display_order ASC,
     *            meal_name ASC
     *
     * Example:
     *
     *   displayOrder | mealName
     *   -------------|----------------
     *   1            | Breakfast Meal
     *   2            | Chicken Meal
     *   2            | Vegetarian Meal
     *   3            | Child Meal
     *
     * The secondary Meal-name ordering provides stable and predictable
     * ordering when displayOrder values are equal.
     */
    public static Specification<FlightMeal> orderByDisplayOrder() {

        return (root, query, criteriaBuilder) -> {

            /*
             * Modify the CriteriaQuery by adding ORDER BY expressions.
             *
             * root.get("meal").get("name") navigates through the
             * FlightMeal -> Meal relationship to sort by Meal name.
             */
            if (query != null) {

                query.orderBy(
                        criteriaBuilder.asc(
                                root.get("displayOrder")
                        ),
                        criteriaBuilder.asc(
                                root.get("meal").get("name")
                        )
                );
            }

            /*
             * This specification modifies query ordering only.
             * It does not add a WHERE condition, so an always-true
             * predicate is returned.
             */
            return criteriaBuilder.conjunction();
        };
    }


    /**
     * Builds a dynamic FlightMeal query by combining optional filters.
     *
     * Each individual specification handles null values by returning an
     * always-true condition. Therefore, the caller can pass any combination
     * of filters without manually constructing different queries.
     *
     * Example 1:
     *
     *   buildSpecification(1L, null, true)
     *
     * Conceptually produces:
     *
     *   WHERE flight_id = 1
     *     AND available = true
     *
     *
     * Example 2:
     *
     *   buildSpecification(1L, 5L, true)
     *
     * Conceptually produces:
     *
     *   WHERE flight_id = 1
     *     AND meal_id = 5
     *     AND available = true
     *
     *
     * Example 3:
     *
     *   buildSpecification(null, null, true)
     *
     * Conceptually produces:
     *
     *   WHERE available = true
     *
     * This avoids creating separate repository methods for every possible
     * combination of search parameters.
     */
    public static Specification<FlightMeal> buildSpecification(
            Long flightId,
            Long mealId,
            Boolean available
    ) {

        return Specification
                .where(hasFlightId(flightId))
                .and(hasMealId(mealId))
                .and(isAvailable(available));
    }
}