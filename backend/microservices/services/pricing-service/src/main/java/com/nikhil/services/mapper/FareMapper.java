package com.nikhil.services.mapper;

import com.nikhil.common_lib.embeddable.*;
import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.services.model.Fare;

/*
 * Maps Fare API DTOs to the Fare persistence model and back.
 *
 * Fare benefit flags are grouped into embedded value objects such as
 * SeatBenefits, BoardingBenefits, and FlexibilityBenefits before persistence.
 */
public class FareMapper {


    /**
     * Converts a FareRequest into a new Fare entity.
     *
     * Groups individual benefit flags from the request into their corresponding
     * embedded benefit objects and calculates the initial current price when
     * the caller does not provide one.
     */
    public static Fare toEntity(
            FareRequest request
    ) {

        if (request == null) {
            return null;
        }

        /*
         * Group seat-related fare benefits into the embedded SeatBenefits
         * object stored as part of the Fare entity.
         */
        SeatBenefits seatBenefits =
                SeatBenefits.builder()
                        .extraSeatSpace(
                                bool(request.getExtraSeatSpace())
                        )
                        .preferredSeatChoice(
                                bool(request.getPreferredSeatChoice())
                        )
                        .advanceSeatSelection(
                                bool(request.getAdvanceSeatSelection())
                        )
                        .guaranteedSeatTogether(
                                bool(request.getGuaranteedSeatTogether())
                        )
                        .build();


        /*
         * Group airport and boarding privileges into the embedded
         * BoardingBenefits object.
         */
        BoardingBenefits boardingBenefits =
                BoardingBenefits.builder()
                        .priorityBoarding(
                                bool(request.getPriorityBoarding())
                        )
                        .priorityCheckin(
                                bool(request.getPriorityCheckin())
                        )
                        .fastTrackSecurity(
                                bool(request.getFastTrackSecurity())
                        )
                        .build();


        /*
         * Group meal, connectivity, entertainment, and beverage benefits
         * into the embedded InFlightBenefits object.
         */
        InFlightBenefits inFlightBenefits =
                InFlightBenefits.builder()
                        .complimentaryMeals(
                                bool(request.getComplimentaryMeals())
                        )
                        .premiumMealChoice(
                                bool(request.getPremiumMealChoice())
                        )
                        .inFlightInternet(
                                bool(request.getInFlightInternet())
                        )
                        .inFlightEntertainment(
                                bool(request.getInFlightEntertainment())
                        )
                        .complimentaryBeverages(
                                bool(request.getComplimentaryBeverages())
                        )
                        .build();


        /*
         * Group fare modification and refund privileges into the embedded
         * FlexibilityBenefits object.
         */
        FlexibilityBenefits flexibilityBenefits =
                FlexibilityBenefits.builder()
                        .freeDateChange(
                                bool(request.getFreeDateChange())
                        )
                        .partialRefund(
                                bool(request.getPartialRefund())
                        )
                        .fullRefund(
                                bool(request.getFullRefund())
                        )
                        .build();


        /*
         * Group premium travel services into the embedded
         * PremiumServiceBenefits object.
         */
        PremiumServiceBenefits premiumServiceBenefits =
                PremiumServiceBenefits.builder()
                        .loungeAccess(
                                bool(request.getLoungeAccess())
                        )
                        .airportTransfer(
                                bool(request.getAirportTransfer())
                        )
                        .build();


        /*
         * Use the explicitly supplied current price when available.
         *
         * Otherwise initialize it from the base fare and applicable charges.
         * Null optional charges are treated as zero.
         */
        Double calculatedPrice =
                request.getCurrentPrice();

        if (calculatedPrice == null) {

            calculatedPrice =
                    request.getBaseFare()
                            + (
                            request.getTaxesAndFees() != null
                                    ? request.getTaxesAndFees()
                                    : 0.0
                    )
                            + (
                            request.getAirlineFees() != null
                                    ? request.getAirlineFees()
                                    : 0.0
                    );
        }


        /*
         * Build the Fare entity using the pricing fields, cross-service
         * references, and grouped embedded benefit objects.
         */
        return Fare.builder()
                .name(request.getName())
                .rbdCode(request.getRbdCode())

                // Cross-service references owned by Flight Ops and Seat services.
                .flightId(request.getFlightId())
                .cabinClassId(request.getCabinClassId())

                // Fare pricing components.
                .baseFare(request.getBaseFare())
                .taxesAndFees(request.getTaxesAndFees())
                .airlineFees(request.getAirlineFees())
                .currentPrice(calculatedPrice)

                .fareLabel(request.getFareLabel())

                // Embedded benefit groups.
                .seatBenefits(seatBenefits)
                .boardingBenefits(boardingBenefits)
                .inFlightBenefits(inFlightBenefits)
                .flexibilityBenefits(flexibilityBenefits)
                .premiumServiceBenefits(premiumServiceBenefits)

                .build();
    }


    /**
     * Converts a Fare entity into the API response model.
     *
     * Embedded benefit objects are flattened into individual response fields
     * so API consumers do not need to understand the persistence structure.
     */
    public static FareResponse toResponse(
            Fare fare
    ) {

        if (fare == null) {
            return null;
        }

        return FareResponse.builder()

                // Core fare identity and cross-service references.
                .id(fare.getId())
                .name(fare.getName())
                .rbdCode(fare.getRbdCode())
                .flightId(fare.getFlightId())
                .cabinClassId(fare.getCabinClassId())
                .cabinClass(fare.getCabinClass())

                // Pricing details.
                .baseFare(fare.getBaseFare())
                .taxesAndFees(fare.getTaxesAndFees())
                .airlineFees(fare.getAirlineFees())
                .currentPrice(fare.getCurrentPrice())
                .totalPrice(fare.getTotalPrice())
                .fareLabel(fare.getFareLabel())

                // Reference to the associated fare rules.
                .fareRulesId(
                        fare.getFareRules() != null
                                ? fare.getFareRules().getId()
                                : null
                )


                // ==================== Seat Benefits ====================

                .extraSeatSpace(
                        fare.getSeatBenefits() != null
                                ? fare.getSeatBenefits().getExtraSeatSpace()
                                : false
                )
                .preferredSeatChoice(
                        fare.getSeatBenefits() != null
                                ? fare.getSeatBenefits().getPreferredSeatChoice()
                                : false
                )
                .advanceSeatSelection(
                        fare.getSeatBenefits() != null
                                ? fare.getSeatBenefits().getAdvanceSeatSelection()
                                : false
                )
                .guaranteedSeatTogether(
                        fare.getSeatBenefits() != null
                                ? fare.getSeatBenefits().getGuaranteedSeatTogether()
                                : false
                )


                // ==================== Boarding Benefits ====================

                .priorityBoarding(
                        fare.getBoardingBenefits() != null
                                ? fare.getBoardingBenefits().getPriorityBoarding()
                                : false
                )
                .priorityCheckin(
                        fare.getBoardingBenefits() != null
                                ? fare.getBoardingBenefits().getPriorityCheckin()
                                : false
                )
                .fastTrackSecurity(
                        fare.getBoardingBenefits() != null
                                ? fare.getBoardingBenefits().getFastTrackSecurity()
                                : false
                )


                // ==================== In-Flight Benefits ====================

                .complimentaryMeals(
                        fare.getInFlightBenefits() != null
                                ? fare.getInFlightBenefits().getComplimentaryMeals()
                                : false
                )
                .premiumMealChoice(
                        fare.getInFlightBenefits() != null
                                ? fare.getInFlightBenefits().getPremiumMealChoice()
                                : false
                )
                .inFlightInternet(
                        fare.getInFlightBenefits() != null
                                ? fare.getInFlightBenefits().getInFlightInternet()
                                : false
                )
                .inFlightEntertainment(
                        fare.getInFlightBenefits() != null
                                ? fare.getInFlightBenefits().getInFlightEntertainment()
                                : false
                )
                .complimentaryBeverages(
                        fare.getInFlightBenefits() != null
                                ? fare.getInFlightBenefits().getComplimentaryBeverages()
                                : false
                )


                // ==================== Flexibility Benefits ====================

                .freeDateChange(
                        fare.getFlexibilityBenefits() != null
                                ? fare.getFlexibilityBenefits().getFreeDateChange()
                                : false
                )
                .partialRefund(
                        fare.getFlexibilityBenefits() != null
                                ? fare.getFlexibilityBenefits().getPartialRefund()
                                : false
                )
                .fullRefund(
                        fare.getFlexibilityBenefits() != null
                                ? fare.getFlexibilityBenefits().getFullRefund()
                                : false
                )


                // ==================== Premium Service Benefits ====================

                .loungeAccess(
                        fare.getPremiumServiceBenefits() != null
                                ? fare.getPremiumServiceBenefits().getLoungeAccess()
                                : false
                )
                .airportTransfer(
                        fare.getPremiumServiceBenefits() != null
                                ? fare.getPremiumServiceBenefits().getAirportTransfer()
                                : false
                )


                /*
                 * Map related FareRules and BaggagePolicy objects only when
                 * those relationships are available on the Fare entity.
                 */
                .fareRules(
                        fare.getFareRules() != null
                                ? FareRulesMapper.toResponse(
                                fare.getFareRules()
                        )
                                : null
                )
                .baggagePolicy(
                        fare.getBaggagePolicy() != null
                                ? BaggagePolicyMapper.toResponse(
                                fare.getBaggagePolicy()
                        )
                                : null
                )

                // Audit timestamps.
                .createdAt(fare.getCreatedAt())
                .updatedAt(fare.getUpdatedAt())

                .build();
    }


    /**
     * Applies non-null request values to an existing Fare entity.
     *
     * This supports partial field updates at the mapper level: null request
     * fields preserve the existing entity value instead of overwriting it.
     */
    public static void updateEntity(
            FareRequest request,
            Fare existing
    ) {

        if (request == null || existing == null) {
            return;
        }


        // Update core fare fields only when a new value is provided.

        if (request.getName() != null) {
            existing.setName(request.getName());
        }

        if (request.getRbdCode() != null) {
            existing.setRbdCode(request.getRbdCode());
        }

        if (request.getFlightId() != null) {
            existing.setFlightId(request.getFlightId());
        }

        if (request.getCabinClassId() != null) {
            existing.setCabinClassId(
                    request.getCabinClassId()
            );
        }


        // Update pricing fields only when explicitly supplied.

        if (request.getBaseFare() != null) {
            existing.setBaseFare(
                    request.getBaseFare()
            );
        }

        if (request.getTaxesAndFees() != null) {
            existing.setTaxesAndFees(
                    request.getTaxesAndFees()
            );
        }

        if (request.getAirlineFees() != null) {
            existing.setAirlineFees(
                    request.getAirlineFees()
            );
        }

        if (request.getCurrentPrice() != null) {
            existing.setCurrentPrice(
                    request.getCurrentPrice()
            );
        }

        if (request.getFareLabel() != null) {
            existing.setFareLabel(
                    request.getFareLabel()
            );
        }


        /*
         * Update individual values inside each embedded benefit object.
         * Unspecified request fields retain their existing values.
         */

        SeatBenefits seatBenefits =
                existing.getSeatBenefits();

        if (request.getExtraSeatSpace() != null) {
            seatBenefits.setExtraSeatSpace(
                    request.getExtraSeatSpace()
            );
        }

        if (request.getPreferredSeatChoice() != null) {
            seatBenefits.setPreferredSeatChoice(
                    request.getPreferredSeatChoice()
            );
        }

        if (request.getAdvanceSeatSelection() != null) {
            seatBenefits.setAdvanceSeatSelection(
                    request.getAdvanceSeatSelection()
            );
        }

        if (request.getGuaranteedSeatTogether() != null) {
            seatBenefits.setGuaranteedSeatTogether(
                    request.getGuaranteedSeatTogether()
            );
        }


        BoardingBenefits boardingBenefits =
                existing.getBoardingBenefits();

        if (request.getPriorityBoarding() != null) {
            boardingBenefits.setPriorityBoarding(
                    request.getPriorityBoarding()
            );
        }

        if (request.getPriorityCheckin() != null) {
            boardingBenefits.setPriorityCheckin(
                    request.getPriorityCheckin()
            );
        }

        if (request.getFastTrackSecurity() != null) {
            boardingBenefits.setFastTrackSecurity(
                    request.getFastTrackSecurity()
            );
        }


        InFlightBenefits inFlightBenefits =
                existing.getInFlightBenefits();

        if (request.getComplimentaryMeals() != null) {
            inFlightBenefits.setComplimentaryMeals(
                    request.getComplimentaryMeals()
            );
        }

        if (request.getPremiumMealChoice() != null) {
            inFlightBenefits.setPremiumMealChoice(
                    request.getPremiumMealChoice()
            );
        }

        if (request.getInFlightInternet() != null) {
            inFlightBenefits.setInFlightInternet(
                    request.getInFlightInternet()
            );
        }

        if (request.getInFlightEntertainment() != null) {
            inFlightBenefits.setInFlightEntertainment(
                    request.getInFlightEntertainment()
            );
        }

        if (request.getComplimentaryBeverages() != null) {
            inFlightBenefits.setComplimentaryBeverages(
                    request.getComplimentaryBeverages()
            );
        }


        FlexibilityBenefits flexibilityBenefits =
                existing.getFlexibilityBenefits();

        if (request.getFreeDateChange() != null) {
            flexibilityBenefits.setFreeDateChange(
                    request.getFreeDateChange()
            );
        }

        if (request.getPartialRefund() != null) {
            flexibilityBenefits.setPartialRefund(
                    request.getPartialRefund()
            );
        }

        if (request.getFullRefund() != null) {
            flexibilityBenefits.setFullRefund(
                    request.getFullRefund()
            );
        }


        PremiumServiceBenefits premiumServiceBenefits =
                existing.getPremiumServiceBenefits();

        if (request.getLoungeAccess() != null) {
            premiumServiceBenefits.setLoungeAccess(
                    request.getLoungeAccess()
            );
        }

        if (request.getAirportTransfer() != null) {
            premiumServiceBenefits.setAirportTransfer(
                    request.getAirportTransfer()
            );
        }
    }


    /**
     * Converts nullable Boolean request values into primitive boolean values.
     *
     * Missing benefit flags default to false when creating a new Fare.
     */
    private static boolean bool(
            Boolean value
    ) {

        return value != null
                ? value
                : false;
    }
}