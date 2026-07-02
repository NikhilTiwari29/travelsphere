package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/*
 * Physical seat layout for one cabin class on an aircraft.
 *
 * Middle of the hierarchy: CabinClass (1) → SeatMap (1) → Seat (many).
 * SeatServiceImpl.generateSeats() populates Seat rows from map dimensions.
 * Exposed via /api/seat-maps/**; airlineId resolved via AirlineClient Feign.
 */
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
