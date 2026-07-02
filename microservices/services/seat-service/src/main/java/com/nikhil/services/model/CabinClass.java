package com.nikhil.services.model;

import com.nikhil.common_lib.enums.CabinClassType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/*
 * Cabin-class metadata for an aircraft type (Economy, Business, First).
 *
 * Root of seat hierarchy: CabinClass → SeatMap → Seat → SeatInstance.
 * flight-ops-service SeatClient resolves cabinClassId during flight search.
 * Exposed via Gateway /api/cabin-classes/**.
 */
@Entity
@Table(name = "cabin_classes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CabinClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CabinClassType name;

    @Size(max = 5)
    @Column(nullable = false, length = 5)
    private String code;

    @Size(max = 255)
    private String description;

    @OneToOne(mappedBy = "cabinClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private SeatMap seatMap;

    // Cross-service ref: Aircraft is in airline-core-service
    @Column(name = "aircraft_id")
    private Long aircraftId;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isBookable = true;

    private Integer typicalSeatPitch;
    private Integer typicalSeatWidth;
    private String seatType;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

}
