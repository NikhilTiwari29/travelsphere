package com.nikhil.services.specification;

import com.nikhil.services.model.FlightMeal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class FlightMealSpecification {

    public static Specification<FlightMeal> hasFlightId(Long flightId) {
        return (root, query, criteriaBuilder) -> {
            if (flightId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("flightId"), flightId);
        };
    }

    public static Specification<FlightMeal> hasMealId(Long mealId) {
        return (root, query, criteriaBuilder) -> {
            if (mealId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("meal").get("id"), mealId);
        };
    }

    public static Specification<FlightMeal> isAvailable(Boolean available) {
        return (root, query, criteriaBuilder) -> {
            if (available == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("available"), available);
        };
    }

    public static Specification<FlightMeal> hasFlightIdAndMealId(Long flightId, Long mealId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (flightId != null) {
                predicates.add(criteriaBuilder.equal(root.get("flightId"), flightId));
            }
            if (mealId != null) {
                predicates.add(criteriaBuilder.equal(root.get("meal").get("id"), mealId));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<FlightMeal> orderByDisplayOrder() {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.orderBy(
                        criteriaBuilder.asc(root.get("displayOrder")),
                        criteriaBuilder.asc(root.get("meal").get("name"))
                );
            }
            return criteriaBuilder.conjunction();
        };
    }

    public static Specification<FlightMeal> buildSpecification(
            Long flightId, Long mealId, Boolean available) {
        return Specification
                .where(hasFlightId(flightId))
                .and(hasMealId(mealId))
                .and(isAvailable(available));
    }
}
