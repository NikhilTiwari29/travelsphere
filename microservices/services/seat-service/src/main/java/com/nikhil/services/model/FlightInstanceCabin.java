package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    public Integer getAvailableSeats() {
        return totalSeats - bookedSeats;
    }
}
