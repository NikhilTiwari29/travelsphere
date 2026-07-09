package com.nikhil.services.model;

import com.nikhil.common_lib.domain.metadata.AncillaryMetadata;
import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.services.converter.AncillaryMetadataConverter;
import jakarta.persistence.*;
import lombok.*;

/**
 * Master definition of an optional airline product or service
 * that can be sold in addition to the base flight fare.
 *
 * Examples:
 *   - Extra baggage
 *   - Travel insurance
 *   - Lounge access
 *   - Meals
 *   - Wi-Fi
 *   - Airport transfer
 *   - Priority services
 *
 * Ancillaries are owned and configured by individual airlines.
 *
 * Example:
 *
 * Airline
 *    ↓
 * Ancillary
 *    ├── Extra Baggage
 *    ├── Travel Insurance
 *    ├── Lounge Access
 *    └── Meal
 *
 * Type-specific attributes are stored in metadata so that different
 * ancillary categories can maintain different configuration structures
 * without adding category-specific nullable columns to this table.
 */
@Entity
@Table(name = "ancillaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ancillary {

    /**
     * Internal unique identifier of the ancillary product.
     *
     * Generated automatically by the database.
     *
     * Example:
     *   101
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * High-level business category of the ancillary product.
     *
     * Used to classify the service and determine applicable
     * business logic and metadata structure.
     *
     * Examples:
     *   BAGGAGE
     *   INSURANCE
     *   LOUNGE
     *   MEAL
     *   WIFI
     *   TRANSFER
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AncillaryType type;


    /**
     * More specific classification within the main ancillary type.
     *
     * Allows multiple product variations under the same broad category.
     *
     * Examples:
     *
     * type = INSURANCE
     * subType = TRAVEL_INSURANCE
     *
     * type = MEAL
     * subType = VEG_MEAL
     *
     * type = BAGGAGE
     * subType = EXTRA_CHECKED_BAG
     */
    @Column(length = 100)
    private String subType;


    /**
     * Reason For Issuance Sub-Code (RFISC) used to identify
     * the optional service in airline industry processes.
     *
     * This code can be used for service identification across
     * booking, pricing, fulfillment, and electronic miscellaneous
     * document processing flows.
     *
     * Examples:
     *   0B5
     *   0CC
     */
    @Column(length = 10)
    private String rfisc;


    /**
     * Product name displayed to customers or airline operators.
     *
     * Examples:
     *   Premium Travel Insurance
     *   Extra 10 KG Baggage
     *   Business Lounge Access
     *   Vegetarian Meal
     */
    @Column(nullable = false, length = 200)
    private String name;


    /**
     * Detailed description of the ancillary product and its benefits.
     *
     * Used by booking interfaces to explain what the customer receives
     * when purchasing the optional service.
     *
     * Example:
     *   Provides coverage for eligible trip cancellation,
     *   baggage loss, and travel delays.
     */
    @Column(length = 1000)
    private String description;


    /**
     * Flexible type-specific configuration for the ancillary product.
     *
     * Different ancillary categories require different attributes.
     *
     * Examples:
     *
     * INSURANCE:
     *   - Provider details
     *   - Policy type
     *   - Coverage limits
     *
     * MEAL:
     *   - Meal category
     *   - Dietary type
     *
     * LOUNGE:
     *   - Airport
     *   - Terminal
     *   - Access duration
     *
     * AncillaryMetadataConverter converts the structured
     * AncillaryMetadata object to and from the database TEXT value.
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = AncillaryMetadataConverter.class)
    private AncillaryMetadata metadata;


    /**
     * Controls the display priority of the ancillary product.
     *
     * Lower values can be displayed before higher values by the
     * booking UI or ancillary catalog.
     *
     * Example:
     *
     * 1 → Extra Baggage
     * 2 → Travel Insurance
     * 3 → Lounge Access
     */
    private Integer displayOrder;


    /**
     * Identifier of the airline that owns and offers this ancillary.
     *
     * This is a cross-service reference.
     *
     * The Airline entity belongs to airline-core-service, therefore
     * this service stores only the airline ID and does not create a
     * JPA relationship such as @ManyToOne.
     *
     * Ownership resolution flow:
     *
     * User ID
     *    ↓
     * AirlineIntegrationService
     *    ↓
     * airline-core-service
     *    ↓
     * Airline ID
     *    ↓
     * Ancillary.airlineId
     */
    @Column(name = "airline_id", nullable = false)
    private Long airlineId;
}