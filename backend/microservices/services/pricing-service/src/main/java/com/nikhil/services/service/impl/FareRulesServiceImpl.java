package com.nikhil.services.service.impl;

import com.nikhil.common_lib.payload.request.FareRulesRequest;
import com.nikhil.common_lib.payload.response.FareRulesResponse;
import com.nikhil.services.exception.FareAlreadyExistsException;
import com.nikhil.services.exception.FareNotFoundException;
import com.nikhil.services.exception.FareRuleAlreadyExistException;
import com.nikhil.services.exception.FareRuleNotFoundException;
import com.nikhil.services.mapper.FareRulesMapper;
import com.nikhil.services.model.Fare;
import com.nikhil.services.model.FareRules;
import com.nikhil.services.repository.FareRepository;
import com.nikhil.services.repository.FareRulesRepository;
import com.nikhil.services.service.FareRulesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation responsible for managing fare-rule configurations.
 *
 * FareRules define the change, cancellation, refund, and other flexibility
 * conditions associated with a Fare.
 *
 * Relationship:
 *
 * Fare
 *   ↓
 * FareRules
 *
 * A Fare can have only one FareRules configuration.
 *
 * Main responsibilities:
 *   - Create rules for a fare
 *   - Prevent duplicate rule configurations for the same fare
 *   - Retrieve rules by ID or Fare ID
 *   - Retrieve airline-specific fare rules
 *   - Update existing fare rules
 *   - Delete fare rules
 *
 * Transaction Strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, update, and delete operations explicitly override the
 *     class-level transaction with writable transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FareRulesServiceImpl implements FareRulesService {

    private final FareRulesRepository fareRulesRepository;
    private final FareRepository fareRepository;


    /**
     * Creates a FareRules configuration for an existing fare.
     *
     * Processing flow:
     *
     * FareRulesRequest
     *        ↓
     * Validate Fare existence
     *        ↓
     * Check duplicate FareRules
     *        ↓
     * Map request to entity
     *        ↓
     * Persist FareRules
     *        ↓
     * Return FareRulesResponse
     *
     * Only one FareRules configuration is allowed for each Fare.
     *
     * @param request fare-rule creation request
     * @return created fare-rule configuration
     */
    @Override
    @Transactional
    public FareRulesResponse createFareRules(
            FareRulesRequest request
    ) {

        log.info(
                "Creating fare rules fareId={}",
                request.getFareId()
        );

        /*
         * Validate that the referenced Fare exists before creating
         * the FareRules relationship.
         */
        Fare fare = fareRepository.findById(request.getFareId())
                .orElseThrow(() -> {

                    log.warn(
                            "Cannot create fare rules because fare was not found fareId={}",
                            request.getFareId()
                    );

                    return new FareNotFoundException(request.getFareId());
                });

        /*
         * Enforce one-to-one business rule:
         *
         * One Fare can have only one FareRules configuration.
         */
        if (fareRulesRepository.existsByFareId(request.getFareId())) {

            log.warn(
                    "Cannot create fare rules because rules already exist fareId={}",
                    request.getFareId()
            );

            throw new FareAlreadyExistsException(request.getFareId());
        }

        /*
         * Convert the incoming request into a FareRules entity and
         * associate it with the validated Fare.
         */
        FareRules fareRules =
                FareRulesMapper.toEntity(request, fare);

        FareRules saved =
                fareRulesRepository.save(fareRules);

        log.info(
                "Fare rules created successfully fareRulesId={} fareId={}",
                saved.getId(),
                request.getFareId()
        );

        return FareRulesMapper.toResponse(saved);
    }


    @Override
    @Transactional
    public List<FareRulesResponse> createFareRules(
            List<FareRulesRequest> requests
    ) {

        log.info(
                "Bulk fare rules creation started requestedCount={}",
                requests.size()
        );

        List<FareRules> toSave =
                requests.stream()
                        .map(request -> {

                            Fare fare =
                                    fareRepository.findById(request.getFareId())
                                            .orElseThrow(() -> {

                                                log.warn(
                                                        "Fare not found fareId={}",
                                                        request.getFareId()
                                                );

                                                return new FareNotFoundException(request.getFareId());
                                            });

                            if (fareRulesRepository.existsByFareId(
                                    request.getFareId()
                            )) {

                                log.warn(
                                        "Skipping fare rules creation because rules already exist fareId={}",
                                        request.getFareId()
                                );

                                throw new FareRuleAlreadyExistException(request.getFareId());
                            }

                            return FareRulesMapper.toEntity(
                                    request,
                                    fare
                            );
                        })
                        .toList();

        List<FareRules> saved =
                fareRulesRepository.saveAll(toSave);

        log.info(
                "Bulk fare rules creation completed createdCount={}",
                saved.size()
        );

        return saved.stream()
                .map(FareRulesMapper::toResponse)
                .toList();
    }


    /**
     * Retrieves FareRules using their primary identifier.
     *
     * @param id fare-rules ID
     * @return fare-rule details
     */
    @Override
    public FareRulesResponse getFareRulesById(
            Long id
    ) {

        log.debug(
                "Fetching fare rules fareRulesId={}",
                id
        );

        FareRules fareRules =
                fareRulesRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Fare rules not found fareRulesId={}",
                                    id
                            );

                            return new FareRuleNotFoundException(id);
                        });

        log.debug(
                "Fare rules fetched successfully fareRulesId={}",
                id
        );

        return FareRulesMapper.toResponse(fareRules);
    }


    /**
     * Retrieves the FareRules configuration associated with a specific Fare.
     *
     * This operation is commonly used by booking and fare-display flows
     * when evaluating the flexibility conditions of a selected fare.
     *
     * @param fareId fare ID
     * @return fare rules associated with the fare
     */
    @Override
    public FareRulesResponse getFareRulesByFareId(
            Long fareId
    ) {

        log.debug(
                "Fetching fare rules by fareId={}",
                fareId
        );

        FareRules fareRules =
                fareRulesRepository.findByFareId(fareId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Fare rules not found for fareId={}",
                                    fareId
                            );

                            return new FareRuleNotFoundException(fareId);
                        });

        log.debug(
                "Fare rules fetched successfully fareId={} fareRulesId={}",
                fareId,
                fareRules.getId()
        );

        return FareRulesMapper.toResponse(fareRules);
    }


    /**
     * Retrieves all FareRules configurations associated with an airline.
     *
     * This operation is useful for airline administration and fare-management
     * interfaces where all configured fare rules for an airline are required.
     *
     * @param airlineId airline ID
     * @return list of fare-rule configurations
     */
    @Override
    public List<FareRulesResponse> getFareRulesByAirlineId(
            Long airlineId
    ) {

        log.debug(
                "Fetching fare rules by airlineId={}",
                airlineId
        );

        List<FareRulesResponse> responses =
                fareRulesRepository.findByAirlineId(airlineId)
                        .stream()
                        .map(FareRulesMapper::toResponse)
                        .toList();

        log.debug(
                "Fare rules fetched successfully airlineId={} count={}",
                airlineId,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates an existing FareRules configuration.
     *
     * The existing entity is loaded first and then updated through the mapper.
     * The relationship with the existing Fare remains unchanged unless the
     * mapper explicitly supports changing that relationship.
     *
     * @param id fare-rules ID
     * @param request updated fare-rule values
     * @return updated fare-rule configuration
     */
    @Override
    @Transactional
    public FareRulesResponse updateFareRules(
            Long id,
            FareRulesRequest request
    ) {

        log.info(
                "Updating fare rules fareRulesId={} requestedFareId={}",
                id,
                request.getFareId()
        );

        FareRules existing =
                fareRulesRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update fare rules because record was not found fareRulesId={}",
                                    id
                            );

                            return new FareRuleNotFoundException(id);
                        });

        /*
         * Apply request values to the managed entity.
         *
         * Because this method runs inside a writable transaction,
         * JPA dirty checking can persist changes automatically.
         */
        FareRulesMapper.updateEntity(
                request,
                existing
        );

        FareRules saved =
                fareRulesRepository.save(existing);

        log.info(
                "Fare rules updated successfully fareRulesId={}",
                saved.getId()
        );

        return FareRulesMapper.toResponse(saved);
    }


    /**
     * Deletes an existing FareRules configuration.
     *
     * The record is first loaded to ensure that a meaningful
     * exception is returned when the ID does not exist.
     *
     * @param id fare-rules ID
     */
    @Override
    @Transactional
    public void deleteFareRules(
            Long id
    ) {

        log.info(
                "Deleting fare rules fareRulesId={}",
                id
        );

        FareRules fareRules =
                fareRulesRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete fare rules because record was not found fareRulesId={}",
                                    id
                            );

                            return new FareRuleNotFoundException(id);
                        });

        fareRulesRepository.delete(fareRules);

        log.info(
                "Fare rules deleted successfully fareRulesId={}",
                id
        );
    }
}