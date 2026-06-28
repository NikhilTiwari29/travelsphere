package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "seat_maps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private int totalRows;

    @Column(nullable = false)
    private int rightSeatsPerRow;

    @Column(nullable = false)
    private int leftSeatsPerRow;

    // Cross-service ref: Airline is in airline-core-service
    @Column(name = "airline_id", nullable = false)
    private Long airlineId;

    @OneToMany(mappedBy = "seatMap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats;

    @OneToOne
    @JoinColumn(name = "cabin_class_id")
    private CabinClass cabinClass;
}
