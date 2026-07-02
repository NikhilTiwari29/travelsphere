package com.nikhil.services.model;

import com.nikhil.common_lib.embeddable.*;
import com.nikhil.common_lib.enums.CabinClassType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for a sellable fare bucket on a flight + cabin class combination.
 * flightId references flight-ops-service; cabinClassId references seat-service.
 * Owns 1:1 BaggagePolicy and FareRules in this bounded context. Used by search
 * (lowest price), booking (fareId selection), and airline fare management UIs.
 */
@Entity
@Table(name = "fares")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Fare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Character rbdCode;

    // Cross-service ref: Flight is in flight-ops-service
    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    // Cross-service ref: CabinClass is in seat-service
    @Column(name = "cabin_class_id", nullable = false)
    private Long cabinClassId;

    /**
     * Denormalised cabin class type (mirrors the type stored in seat-service's CabinClass).
     * Stored here so flight search can filter fares by cabin type without a cross-service join.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_class", length = 20)
    private CabinClassType cabinClass;

    // Pricing
    @Column(nullable = false)
    private Double baseFare;

    private Double taxesAndFees;
    private Double airlineFees;

    @Column(nullable = false)
    private Double currentPrice;

    @Column(length = 100)
    private String fareLabel;

    // Same bounded context relationships
    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private BaggagePolicy baggagePolicy;

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private FareRules fareRules;

    // Embedded benefits
    @Embedded
    @Builder.Default
    private SeatBenefits seatBenefits = new SeatBenefits();

    @Embedded
    @Builder.Default
    private BoardingBenefits boardingBenefits = new BoardingBenefits();

    @Embedded
    @Builder.Default
    private InFlightBenefits inFlightBenefits = new InFlightBenefits();

    @Embedded
    @Builder.Default
    private FlexibilityBenefits flexibilityBenefits = new FlexibilityBenefits();

    @Embedded
    @Builder.Default
    private PremiumServiceBenefits premiumServiceBenefits = new PremiumServiceBenefits();

    // Audit
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void preCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.seatBenefits == null) this.seatBenefits = new SeatBenefits();
        if (this.boardingBenefits == null) this.boardingBenefits = new BoardingBenefits();
        if (this.inFlightBenefits == null) this.inFlightBenefits = new InFlightBenefits();
        if (this.flexibilityBenefits == null) this.flexibilityBenefits = new FlexibilityBenefits();
        if (this.premiumServiceBenefits == null) this.premiumServiceBenefits = new PremiumServiceBenefits();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Double getTotalPrice() {
        return baseFare
                + (airlineFees != null ? airlineFees : 0.0)
                + (taxesAndFees != null ? taxesAndFees : 0.0);
    }
}
