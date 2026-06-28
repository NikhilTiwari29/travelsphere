package com.nikhil.common_lib.enums;

/**
 * Broad ancillary types used by GDS systems (Amadeus, Sabre, Travelport).
 * Use subType field in Ancillary entity for specific variants.
 *
 * NOTE: MEAL is not included here as meals are managed via the separate Meal entity
 * due to complex domain requirements (ingredients, allergens, nutritional info, etc.)
 */
public enum AncillaryType {
    BAGGAGE,
    TRAVEL_PROTECTION,

}
