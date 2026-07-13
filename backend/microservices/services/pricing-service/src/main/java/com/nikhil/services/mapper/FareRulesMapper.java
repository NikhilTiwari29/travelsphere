package com.nikhil.services.mapper;

import com.nikhil.common_lib.payload.request.FareRulesRequest;
import com.nikhil.common_lib.payload.response.FareRulesResponse;
import com.nikhil.services.model.Fare;
import com.nikhil.services.model.FareRules;

/**
 * Mapper utility responsible for converting between FareRules request DTOs,
 * FareRules entities, and FareRules response DTOs.
 */
public class FareRulesMapper {

    /**
     * Converts a FareRulesRequest into a new FareRules entity.
     *
     * The Fare entity is resolved and validated by the service layer
     * before being associated with the fare rule.
     *
     * @param request fare rule creation request
     * @param fare    associated Fare entity
     * @return mapped FareRules entity, or null if the request is null
     */
    public static FareRules toEntity(FareRulesRequest request, Fare fare) {
        if (request == null) return null;

        return FareRules.builder()
                .ruleName(request.getRuleName())

                // Associate the validated Fare entity with this rule.
                .fare(fare)

                // Airline ownership is stored for tenant-level data isolation.
                .airlineId(request.getAirlineId())

                .isRefundable(request.getIsRefundable())
                .changeFee(request.getChangeFee())
                .cancellationFee(request.getCancellationFee())
                .refundDeadlineDays(request.getRefundDeadlineDays())
                .changeDeadlineHours(request.getChangeDeadlineHours())

                // Default to false when changeability is not explicitly provided.
                .isChangeable(
                        request.getIsChangeable() != null
                                ? request.getIsChangeable()
                                : false
                )
                .build();
    }

    /**
     * Converts a FareRules entity into a FareRulesResponse DTO.
     *
     * @param fareRules FareRules entity
     * @return mapped response DTO, or null if the entity is null
     */
    public static FareRulesResponse toResponse(FareRules fareRules) {
        if (fareRules == null) return null;

        return FareRulesResponse.builder()
                .id(fareRules.getId())
                .ruleName(fareRules.getRuleName())

                // Return only the Fare identifier instead of exposing the entity relationship.
                .fareId(
                        fareRules.getFare() != null
                                ? fareRules.getFare().getId()
                                : null
                )

                .airlineId(fareRules.getAirlineId())
                .isRefundable(fareRules.getIsRefundable())
                .changeFee(fareRules.getChangeFee())
                .cancellationFee(fareRules.getCancellationFee())
                .refundDeadlineDays(fareRules.getRefundDeadlineDays())
                .changeDeadlineHours(fareRules.getChangeDeadlineHours())
                .isChangeable(fareRules.getIsChangeable())
                .createdAt(fareRules.getCreatedAt())
                .updatedAt(fareRules.getUpdatedAt())
                .build();
    }

    /**
     * Applies non-null request fields to an existing FareRules entity.
     *
     * This method follows partial-update semantics: null request values are
     * ignored so that existing persisted values remain unchanged.
     *
     * The Fare relationship is intentionally not modified here and should be
     * handled explicitly in the service layer if reassignment is supported.
     *
     * @param request  fare rule update request
     * @param existing existing FareRules entity to update
     */
    public static void updateEntity(
            FareRulesRequest request,
            FareRules existing
    ) {
        if (request == null || existing == null) return;

        if (request.getRuleName() != null) {
            existing.setRuleName(request.getRuleName());
        }

        if (request.getAirlineId() != null) {
            existing.setAirlineId(request.getAirlineId());
        }

        if (request.getIsRefundable() != null) {
            existing.setIsRefundable(request.getIsRefundable());
        }

        if (request.getChangeFee() != null) {
            existing.setChangeFee(request.getChangeFee());
        }

        if (request.getCancellationFee() != null) {
            existing.setCancellationFee(request.getCancellationFee());
        }

        if (request.getRefundDeadlineDays() != null) {
            existing.setRefundDeadlineDays(request.getRefundDeadlineDays());
        }

        if (request.getChangeDeadlineHours() != null) {
            existing.setChangeDeadlineHours(request.getChangeDeadlineHours());
        }

        if (request.getIsChangeable() != null) {
            existing.setIsChangeable(request.getIsChangeable());
        }
    }
}