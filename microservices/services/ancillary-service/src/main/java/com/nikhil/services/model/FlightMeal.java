package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "flight_meals", indexes = {
        @Index(name = "idx_flight_meal_flight", columnList = "flight_id"),
        @Index(name = "idx_flight_meal_meal", columnList = "meal_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_flight_meal", columnNames = {"flight_id", "meal_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service reference: Flight lives in flight-ops-service
    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    // Same bounded context: Meal lives in this service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    private Double price;

    @Builder.Default
    private Integer displayOrder = 0;
}
