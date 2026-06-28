package com.nikhil.services.model;

import com.nikhil.common_lib.enums.FlightStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "flights")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@ToString(exclude = {})
@EqualsAndHashCode(of = "id")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String flightNumber;

    // Cross-service ref: Airline is in airline-core-service
    @Column(name = "airline_id", nullable = false)
    private Long airlineId;

    // Cross-service ref: Aircraft is in airline-core-service
    @Column(name = "aircraft_id", nullable = false)
    private Long aircraftId;

    // Cross-service ref: Airport is in location-service
    @Column(name = "departure_airport_id", nullable = false)
    private Long departureAirportId;

    // Cross-service ref: Airport is in location-service
    @Column(name = "arrival_airport_id", nullable = false)
    private Long arrivalAirportId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FlightStatus status;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void preCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
