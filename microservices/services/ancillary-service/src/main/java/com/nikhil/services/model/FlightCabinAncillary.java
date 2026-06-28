package com.nikhil.services.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "flight_cabin_ancillaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightCabinAncillary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service reference: Flight lives in flight-ops-service
    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    // Cross-service reference: CabinClass lives in seat-service
    @Column(name = "cabin_class_id", nullable = false)
    private Long cabinClassId;

    // Same bounded context: Ancillary lives in this service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancillary_id", nullable = false)
    private Ancillary ancillary;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    private Integer maxQuantity;

    private Double price;

    private String currency;

    @Column(nullable = false)
    @Builder.Default
    private Boolean includedInFare = false;
}
