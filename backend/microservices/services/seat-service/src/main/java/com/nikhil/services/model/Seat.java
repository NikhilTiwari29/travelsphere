package com.nikhil.services.model;

import com.nikhil.common_lib.enums.SeatType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/*
 * Template seat row within a SeatMap layout.
 *
 * Part of: SeatMap → Seat → SeatInstance (runtime copy per flight instance).
 * Holds static attributes (number, type, premium flags); availability and
 * booking state live on SeatInstance. Exposed via /api/seats/** gateway route.
 */
@Entity
@Table(name = "seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(min = 2, max = 10)
    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Positive
    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "column_letter")
    private Character columnLetter;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Column(name = "base_price")
    private Double basePrice;

    @Column(name = "premium_surcharge")
    private Double premiumSurcharge;

    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Builder.Default
    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked = false;

    @Builder.Default
    @Column(name = "is_emergency_exit", nullable = false)
    private Boolean isEmergencyExit = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "has_extra_legroom", nullable = false)
    private Boolean hasExtraLegroom = false;

    @Builder.Default
    @Column(name = "has_bassinet", nullable = false)
    private Boolean hasBassinet = false;

    @Builder.Default
    @Column(name = "is_near_lavatory", nullable = false)
    private Boolean isNearLavatory = false;

    @Builder.Default
    @Column(name = "is_near_galley", nullable = false)
    private Boolean isNearGalley = false;

    @Builder.Default
    @Column(name = "has_power_outlet", nullable = false)
    private Boolean hasPowerOutlet = false;

    @Builder.Default
    @Column(name = "has_tv_screen", nullable = false)
    private Boolean hasTvScreen = false;

    @Builder.Default
    @Column(name = "is_wheelchair_accessible", nullable = false)
    private Boolean isWheelchairAccessible = false;

    @Builder.Default
    @Column(name = "has_extra_width", nullable = false)
    private Boolean hasExtraWidth = false;

    private Integer seatPitch;
    private Integer seatWidth;
    private Integer reclineAngle;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_map_id", nullable = false)
    private SeatMap seatMap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_class_id", foreignKey = @ForeignKey(name = "fk_seat_cabinclass"))
    private CabinClass cabinClass;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    /*
     * Computes base + premium surcharge for display and pricing helpers.
     */
    public Double getTotalPrice() {
        Double total = basePrice != null ? basePrice : 0.0;
        if (premiumSurcharge != null) {
            total = total + premiumSurcharge;
        }
        return total;
    }

    /*
     * True when seat is active, available, and not blocked in the template map.
     */
    public boolean isBookable() {
        return isActive && isAvailable && !isBlocked;
    }

    public boolean isPremiumSeat() {

        return hasExtraLegroom || isEmergencyExit || hasExtraWidth;
    }

    public String getFullPosition() {
        return seatRow + "" + columnLetter;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (seatNumber == null) {
            seatNumber = seatRow + "" + columnLetter;
        }
        if (isEmergencyExit && !hasExtraLegroom) {
            hasExtraLegroom = true;
        }
    }

    public String getSeatCharacteristics() {
        StringBuilder characteristics = new StringBuilder();
        if (hasExtraLegroom) characteristics.append("Extra Legroom, ");
        if (isEmergencyExit) characteristics.append("Emergency Exit, ");
        if (hasBassinet) characteristics.append("Bassinet, ");
        if (hasPowerOutlet) characteristics.append("Power, ");
        if (hasTvScreen) characteristics.append("TV, ");
        if (isWheelchairAccessible) characteristics.append("Wheelchair Access, ");
        if (characteristics.length() > 0) {
            characteristics.setLength(characteristics.length() - 2);
        }
        return characteristics.toString();
    }
}
