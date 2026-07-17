package com.nikhil.services.model;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Entity
@Table(name = "seat_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service ref: Flight is in flight-ops-service
    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_instance_cabin_id")
    private FlightInstanceCabin flightInstanceCabin;

    // Cross-service ref: FlightInstance is in flight-ops-service
    @Column(name = "flight_instance_id")
    private Long flightInstanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatAvailabilityStatus status = SeatAvailabilityStatus.AVAILABLE;

    private boolean isBooked = false;
    private boolean isAvailable = true;

    private String mealPreference;
    private Double fare;
    private Double premiumSurcharge;

    @Version
    private Long version;

    // Cross-service ref
    @Column(name = "flight_schedule_id")
    private Long flightScheduleId;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
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
