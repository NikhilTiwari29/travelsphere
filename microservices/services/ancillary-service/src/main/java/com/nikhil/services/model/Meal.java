package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "meals", indexes = {
        @Index(name = "idx_meal_airline", columnList = "airline_id"),
        @Index(name = "idx_meal_code", columnList = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String mealType;

    @Column(length = 100)
    private String dietaryRestriction;

    @Column(length = 2000)
    private String ingredients;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean requiresAdvanceBooking = false;

    private Integer advanceBookingHours;

    @Builder.Default
    private Integer displayOrder = 0;

    // Cross-service reference: Airline lives in airline-core-service
    @Column(name = "airline_id", nullable = false)
    private Long airlineId;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
