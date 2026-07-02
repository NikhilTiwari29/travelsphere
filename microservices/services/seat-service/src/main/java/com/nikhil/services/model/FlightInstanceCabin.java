package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/*
 * Cabin-level inventory bucket for one flight instance.
 *
 * Links a flight instance (flight-ops-service) to a CabinClass and its
 * SeatInstances. Created on flight-instance-created Kafka event or via REST.
 * Exposed via /api/flight-instance-cabins/** gateway route.
 */
@Entity
@Table(name = "flight_instance_cabins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cabinClass", "seats"})
@EqualsAndHashCode(exclude = {"cabinClass", "seats"})
public class FlightInstanceCabin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service ref: FlightInstance is in flight-ops-service
    @Column(name = "flight_instance_id", nullable = false)
    private Long flightInstanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabin_class_id", nullable = false)
    private CabinClass cabinClass;

    @Column(nullable = false)
    private Integer totalSeats;

    private Integer bookedSeats = 0;

    @Builder.Default
    @OneToMany(
            mappedBy = "flightInstanceCabin",
            cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SeatInstance> seats = new ArrayList<>();

    /*
     * Derived remaining capacity: totalSeats minus bookedSeats.
     */
    public Integer getAvailableSeats() {
        return totalSeats - bookedSeats;
    }
}
