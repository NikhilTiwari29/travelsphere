package com.nikhil.services.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "fare_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FareRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleName;

    // Cross-service ref: Airline is in airline-core-service
    @Column(name = "airline_id")
    private Long airlineId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fare_id", nullable = false)
    @JsonIgnore
    private Fare fare;

    private Boolean isRefundable;

    @Column(name = "change_fee")
    private Double changeFee;

    @Column(name = "cancellation_fee")
    private Double cancellationFee;

    @Column(name = "refund_deadline_days")
    private Integer refundDeadlineDays;

    @Column(name = "change_deadline_hours")
    private Integer changeDeadlineHours;

    @Builder.Default
    private Boolean isChangeable = false;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    public void preCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
