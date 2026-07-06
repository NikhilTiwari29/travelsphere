package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/*
 * Runtime cabin inventory for one cabin class of a specific flight instance.
 *
 * CabinClass defines the static cabin configuration for an aircraft,
 * while FlightInstanceCabin represents that cabin's runtime inventory
 * for one specific scheduled flight occurrence.
 *
 * Example:
 * An airline may operate multiple flight instances on the same route
 * on the same day. Each flight instance gets its own cabin inventory.
 *
 * Hierarchy:
 *
 * FlightInstance
 *      → FlightInstanceCabin (ECONOMY)
 *          → SeatInstances
 *
 *      → FlightInstanceCabin (BUSINESS)
 *          → SeatInstances
 *
 * SeatInstances contain the runtime availability and booking state
 * of the physical Seat records for that specific flight occurrence.
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

    // Unique database identifier for this flight-instance cabin record.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Identifies the specific scheduled flight occurrence this cabin
     * inventory belongs to.
     *
     * FlightInstance is owned by flight-ops-service, so only its ID
     * is stored here instead of creating a cross-service JPA relationship.
     */
    @Column(name = "flight_instance_id", nullable = false)
    private Long flightInstanceId;

    /*
     * Static cabin configuration represented by this runtime cabin inventory.
     *
     * Example: ECONOMY, BUSINESS, or FIRST cabin configured for the aircraft.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabin_class_id", nullable = false)
    private CabinClass cabinClass;

    // Total number of seats provisioned for this cabin on the flight instance.
    @Column(nullable = false)
    private Integer totalSeats;

    // Number of seats currently booked in this cabin.
    private Integer bookedSeats = 0;

    /*
     * Runtime seat inventory for this cabin and flight instance.
     *
     * Each SeatInstance represents the availability and booking state
     * of one physical Seat for this specific flight occurrence.
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "flightInstanceCabin",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SeatInstance> seats = new ArrayList<>();

    // Returns the remaining bookable capacity of this cabin.
    public Integer getAvailableSeats() {
        return totalSeats - bookedSeats;
    }
}