package com.nikhil.services.model;

import com.nikhil.common_lib.embeddable.ContactInfo;
import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.enums.TripType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String bookingReference;

    // Cross-service ref: User (user-service)
    @Column(name = "user_id")
    private Long userId;

    // Cross-service ref: Flight (flight-ops-service)
    @Column(name = "flight_id")
    private Long flightId;

    // Cross-service ref: FlightInstance (flight-ops-service)
    @Column(name = "flight_instance_id")
    private Long flightInstanceId;

    @Column(nullable = false)
    private Long airlineId;

    private TripType tripType = TripType.ONE_WAY;

    @Enumerated(EnumType.STRING)
    private CabinClassType cabinClass = CabinClassType.ECONOMY;

    // Cross-service ref: Fare (pricing-service)
    @Column(name = "fare_id")
    private Long fareId;

    private boolean flexibleTicket;
    private LocalDateTime ticketTimeLimit;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Passenger> passengers = new HashSet<>();

    // Cross-service ref: SeatInstance IDs (seat-service)
    @ElementCollection
    @CollectionTable(name = "booking_seat_instances", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_instance_id")
    private List<Long> seatInstanceIds;

    // Cross-service ref: FlightCabinAncillary IDs (ancillary-service)
    @ElementCollection
    @CollectionTable(name = "booking_ancillaries", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "ancillary_id")
    private List<Long> ancillaryIds;

    // Cross-service ref: FlightMeal IDs (ancillary-service)
    @ElementCollection
    @CollectionTable(name = "booking_meals",
            joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "meal_id")
    private List<Long> mealIds;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private Set<Ticket> tickets=new HashSet<>();

    private Long paymentId;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @CreationTimestamp
    private LocalDateTime bookingDate;

    @UpdateTimestamp
    private LocalDateTime lastModified;

    private boolean ticketIssued;

    private ContactInfo contactInfo;
}
