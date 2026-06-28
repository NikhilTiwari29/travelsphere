package com.nikhil.services.model;

import com.nikhil.common_lib.domain.metadata.AncillaryMetadata;
import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.services.converter.AncillaryMetadataConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ancillaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ancillary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AncillaryType type;

    @Column(length = 100)
    private String subType;

    @Column(length = 10)
    private String rfisc;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AncillaryMetadataConverter.class)
    private AncillaryMetadata metadata;

    private Integer displayOrder;

    // Cross-service reference: Airline lives in airline-core-service
    @Column(name = "airline_id", nullable = false)
    private Long airlineId;
}
