package com.nikhil.services.model;

import com.nikhil.common_lib.enums.CoverageType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "insurance_coverages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Same bounded context: Ancillary lives in this service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancillary_id", nullable = false)
    private Ancillary ancillary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverageType coverageType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double coverageAmount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private boolean isFlat = true;

    @Column(length = 500)
    private String claimCondition;

    @Column(length = 100)
    private String emergencyContact;

    private Integer displayOrder;

    @Builder.Default
    private boolean active = true;
}
