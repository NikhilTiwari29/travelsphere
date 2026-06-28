package com.nikhil.services.specification;

import com.nikhil.services.model.Meal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MealSpecification {

    public static Specification<Meal> hasCode(String code) {
        return (root, query, criteriaBuilder) -> {
            if (code == null || code.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("code"), code);
        };
    }

    public static Specification<Meal> hasAirlineId(Long airlineId) {
        return (root, query, criteriaBuilder) -> {
            if (airlineId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("airlineId"), airlineId);
        };
    }

    public static Specification<Meal> hasMealType(String mealType) {
        return (root, query, criteriaBuilder) -> {
            if (mealType == null || mealType.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("mealType"), mealType);
        };
    }

    public static Specification<Meal> isAvailable(Boolean available) {
        return (root, query, criteriaBuilder) -> {
            if (available == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("available"), available);
        };
    }

    public static Specification<Meal> hasDietaryRestriction(String dietaryRestriction) {
        return (root, query, criteriaBuilder) -> {
            if (dietaryRestriction == null || dietaryRestriction.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("dietaryRestriction"), dietaryRestriction);
        };
    }

    public static Specification<Meal> searchByKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String likePattern = "%" + keyword.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("ingredients")), likePattern)
            );
        };
    }

    public static Specification<Meal> hasCodeAndAirlineId(String code, Long airlineId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (code != null && !code.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("code"), code));
            }
            if (airlineId != null) {
                predicates.add(criteriaBuilder.equal(root.get("airlineId"), airlineId));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Meal> orderByDisplayOrder() {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.orderBy(
                        criteriaBuilder.asc(root.get("displayOrder")),
                        criteriaBuilder.asc(root.get("name"))
                );
            }
            return criteriaBuilder.conjunction();
        };
    }

    public static Specification<Meal> buildSpecification(
            Long airlineId, String mealType, Boolean available,
            String dietaryRestriction, String keyword) {
        return Specification
                .where(hasAirlineId(airlineId))
                .and(hasMealType(mealType))
                .and(isAvailable(available))
                .and(hasDietaryRestriction(dietaryRestriction))
                .and(searchByKeyword(keyword));
    }
}
