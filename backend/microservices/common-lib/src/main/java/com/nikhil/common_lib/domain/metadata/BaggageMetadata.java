package com.nikhil.common_lib.domain.metadata;

import lombok.*;

/**
 * Metadata for BAGGAGE type ancillaries.
 * Follows IATA standards for baggage handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageMetadata {

    private Integer weight;

    // "KG" or "LB"
    private String unit;

    private Integer pieces;

    // "CHECKED", "CABIN", "SPORTS", "OVERSIZED", "SPECIAL"
    private String category;

    // e.g., "55x35x25 cm", "158 cm linear"
    private String dimensions;

    private String notes;
}
