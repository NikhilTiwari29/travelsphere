package com.nikhil.services.service.impl;

import com.nikhil.common_lib.payload.request.BaggagePolicyRequest;
import com.nikhil.common_lib.payload.response.BaggagePolicyResponse;
import com.nikhil.services.mapper.BaggagePolicyMapper;
import com.nikhil.services.model.BaggagePolicy;
import com.nikhil.services.model.Fare;
import com.nikhil.services.repository.BaggagePolicyRepository;
import com.nikhil.services.repository.FareRepository;
import com.nikhil.services.service.BaggagePolicyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service implementation responsible for managing baggage policies
 * associated with airline fares.
 *
 * A BaggagePolicy defines the baggage allowance and restrictions applicable
 * to a particular Fare.
 *
 * Relationship:
 *
 * Fare
 *   ↓
 * BaggagePolicy
 *
 * Each Fare can have only one BaggagePolicy.
 *
 * Main responsibilities:
 *   - Create a baggage policy for a Fare
 *   - Create baggage policies in bulk
 *   - Prevent duplicate policies for the same Fare
 *   - Retrieve policies by ID, Fare ID, or Airline ID
 *   - Update existing baggage policies
 *   - Delete baggage policies
 *
 * Transaction Strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, bulk create, update, and delete operations explicitly
 *     override the class-level configuration with writable transactions.
 *
 * Bulk Creation Strategy:
 *   - Extract all Fare IDs from the incoming requests
 *   - Fetch all required Fare entities in one database query
 *   - Validate that every referenced Fare exists
 *   - Fetch existing policy Fare IDs in one database query
 *   - Skip requests whose Fare already has a BaggagePolicy
 *   - Persist remaining policies using saveAll()
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BaggagePolicyServiceImpl implements BaggagePolicyService {

    private final BaggagePolicyRepository baggagePolicyRepository;
    private final FareRepository fareRepository;


    /**
     * Creates a baggage policy for an existing Fare.
     *
     * Processing flow:
     *
     * BaggagePolicyRequest
     *          ↓
     * Validate Fare existence
     *          ↓
     * Check existing BaggagePolicy
     *          ↓
     * Map request to entity
     *          ↓
     * Persist BaggagePolicy
     *          ↓
     * Return BaggagePolicyResponse
     *
     * Only one BaggagePolicy is allowed for each Fare.
     *
     * @param request baggage-policy creation request
     * @return created baggage policy
     */
    @Override
    @Transactional
    public BaggagePolicyResponse createBaggagePolicy(
            BaggagePolicyRequest request
    ) {

        log.info(
                "Creating baggage policy fareId={}",
                request.getFareId()
        );

        /*
         * Validate that the referenced Fare exists before creating
         * the BaggagePolicy relationship.
         */
        Fare fare = fareRepository.findById(request.getFareId())
                .orElseThrow(() -> {

                    log.warn(
                            "Cannot create baggage policy because fare was not found fareId={}",
                            request.getFareId()
                    );

                    return new EntityNotFoundException(
                            "Fare not found with id: " + request.getFareId()
                    );
                });

        /*
         * Enforce the business rule that a Fare can have only
         * one BaggagePolicy.
         */
        if (baggagePolicyRepository.existsByFareId(request.getFareId())) {

            log.warn(
                    "Cannot create baggage policy because policy already exists fareId={}",
                    request.getFareId()
            );

            throw new IllegalArgumentException(
                    "Baggage policy already exists for fare id: "
                            + request.getFareId()
            );
        }

        /*
         * Convert the request into a BaggagePolicy entity and associate
         * it with the validated Fare.
         */
        BaggagePolicy policy =
                BaggagePolicyMapper.toEntity(request, fare);

        BaggagePolicy saved =
                baggagePolicyRepository.save(policy);

        log.info(
                "Baggage policy created successfully baggagePolicyId={} fareId={}",
                saved.getId(),
                request.getFareId()
        );

        return BaggagePolicyMapper.toResponse(saved);
    }


    /**
     * Creates baggage policies for multiple Fares in a single operation.
     *
     * This implementation avoids the N+1 query problem by batch-fetching
     * Fare entities and existing baggage-policy associations.
     *
     * Processing flow:
     *
     * List<BaggagePolicyRequest>
     *          ↓
     * Extract Fare IDs
     *          ↓
     * Batch-fetch Fare entities
     *          ↓
     * Validate all Fare IDs
     *          ↓
     * Fetch Fare IDs having existing policies
     *          ↓
     * Skip existing policies
     *          ↓
     * Map remaining requests
     *          ↓
     * saveAll()
     *          ↓
     * Return created responses
     *
     * The complete bulk operation executes inside one transaction.
     * If validation or persistence fails, the transaction is rolled back.
     *
     * @param requests baggage-policy creation requests
     * @return list of newly created baggage policies
     */
    @Override
    @Transactional
    public List<BaggagePolicyResponse> createBaggagePolicies(
            List<BaggagePolicyRequest> requests
    ) {

        log.info(
                "Starting bulk baggage policy creation requestedCount={}",
                requests.size()
        );

        /*
         * Extract all Fare IDs referenced by the incoming requests.
         *
         * These IDs are used for batch fetching instead of executing
         * one Fare lookup query for every request.
         */
        List<Long> fareIds = requests.stream()
                .map(BaggagePolicyRequest::getFareId)
                .toList();

        log.debug(
                "Fetching fares for bulk baggage policy creation fareCount={}",
                fareIds.size()
        );

        /*
         * Batch-fetch all referenced Fare entities in a single query
         * and build a lookup map:
         *
         * fareId → Fare
         */
        Map<Long, Fare> fareMap =
                fareRepository.findAllById(fareIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Fare::getId,
                                        fare -> fare
                                )
                        );

        /*
         * Verify that every Fare ID supplied in the request exists.
         *
         * findAllById() does not throw an exception for missing IDs,
         * so explicit validation is required.
         */
        fareIds.forEach(fareId -> {

            if (!fareMap.containsKey(fareId)) {

                log.warn(
                        "Bulk baggage policy creation failed because fare was not found fareId={}",
                        fareId
                );

                throw new EntityNotFoundException(
                        "Fare not found with id: " + fareId
                );
            }
        });

        /*
         * Fetch all Fare IDs that already have a BaggagePolicy.
         *
         * This avoids executing existsByFareId() separately for every
         * request in the bulk payload.
         */
        Set<Long> alreadyHasPolicy =
                baggagePolicyRepository
                        .findFareIdsWithExistingPolicy(fareIds);

        log.debug(
                "Existing baggage policies found during bulk creation existingCount={}",
                alreadyHasPolicy.size()
        );

        /*
         * Skip requests whose Fare already has a policy and convert
         * only eligible requests into BaggagePolicy entities.
         */
        List<BaggagePolicy> policiesToSave =
                requests.stream()
                        .filter(
                                request ->
                                        !alreadyHasPolicy.contains(
                                                request.getFareId()
                                        )
                        )
                        .map(
                                request ->
                                        BaggagePolicyMapper.toEntity(
                                                request,
                                                fareMap.get(
                                                        request.getFareId()
                                                )
                                        )
                        )
                        .toList();

        int skippedCount =
                requests.size() - policiesToSave.size();

        log.info(
                "Bulk baggage policies prepared requestedCount={} eligibleCount={} skippedCount={}",
                requests.size(),
                policiesToSave.size(),
                skippedCount
        );

        /*
         * Persist all eligible baggage policies.
         *
         * saveAll() is executed within the same transaction as validation
         * and preparation of the bulk operation.
         */
        List<BaggagePolicy> savedPolicies =
                baggagePolicyRepository.saveAll(policiesToSave);

        List<BaggagePolicyResponse> responses =
                savedPolicies.stream()
                        .map(BaggagePolicyMapper::toResponse)
                        .toList();

        log.info(
                "Bulk baggage policy creation completed successfully createdCount={} skippedCount={}",
                responses.size(),
                skippedCount
        );

        return responses;
    }


    /**
     * Retrieves a BaggagePolicy using its primary identifier.
     *
     * @param id baggage-policy ID
     * @return baggage-policy details
     */
    @Override
    public BaggagePolicyResponse getBaggagePolicyById(Long id) {

        log.debug(
                "Fetching baggage policy baggagePolicyId={}",
                id
        );

        BaggagePolicy policy =
                baggagePolicyRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Baggage policy not found baggagePolicyId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Baggage policy not found with id: " + id
                            );
                        });

        log.debug(
                "Baggage policy fetched successfully baggagePolicyId={}",
                id
        );

        return BaggagePolicyMapper.toResponse(policy);
    }


    /**
     * Retrieves the BaggagePolicy associated with a specific Fare.
     *
     * This operation is commonly used during fare selection and booking
     * flows to display baggage allowances for the selected fare.
     *
     * @param fareId fare ID
     * @return baggage policy associated with the fare
     */
    @Override
    public BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId) {

        log.debug(
                "Fetching baggage policy by fareId={}",
                fareId
        );

        BaggagePolicy policy =
                baggagePolicyRepository.findByFareId(fareId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Baggage policy not found for fareId={}",
                                    fareId
                            );

                            return new EntityNotFoundException(
                                    "Baggage policy not found for fare id: "
                                            + fareId
                            );
                        });

        log.debug(
                "Baggage policy fetched successfully fareId={} baggagePolicyId={}",
                fareId,
                policy.getId()
        );

        return BaggagePolicyMapper.toResponse(policy);
    }


    /**
     * Retrieves all baggage policies associated with a specific airline.
     *
     * The repository query is responsible for resolving the relationship
     * between Airline, Fare, and BaggagePolicy.
     *
     * @param airlineId airline ID
     * @return baggage policies belonging to the airline
     */
    @Override
    public List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(
            Long airlineId
    ) {

        log.debug(
                "Fetching baggage policies airlineId={}",
                airlineId
        );

        List<BaggagePolicyResponse> responses =
                baggagePolicyRepository.findByAirlineId(airlineId)
                        .stream()
                        .map(BaggagePolicyMapper::toResponse)
                        .toList();

        log.debug(
                "Baggage policies fetched successfully airlineId={} count={}",
                airlineId,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates an existing baggage policy.
     *
     * The existing entity is first loaded into the persistence context.
     * The mapper then applies the request values to the managed entity.
     *
     * Because this method runs inside a writable transaction, Hibernate
     * dirty checking can automatically persist entity changes when the
     * transaction commits.
     *
     * @param id baggage-policy ID
     * @param request updated baggage-policy values
     * @return updated baggage policy
     */
    @Override
    @Transactional
    public BaggagePolicyResponse updateBaggagePolicy(
            Long id,
            BaggagePolicyRequest request
    ) {

        log.info(
                "Updating baggage policy baggagePolicyId={} requestedFareId={}",
                id,
                request.getFareId()
        );

        BaggagePolicy existing =
                baggagePolicyRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update baggage policy because record was not found baggagePolicyId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Baggage policy not found with id: " + id
                            );
                        });

        /*
         * Apply incoming request values to the managed entity.
         */
        BaggagePolicyMapper.updateEntity(request, existing);

        /*
         * Explicit save is retained for clarity.
         *
         * Since 'existing' is a managed JPA entity inside a writable
         * transaction, Hibernate dirty checking would also persist the
         * modifications automatically at transaction commit.
         */
        BaggagePolicy saved =
                baggagePolicyRepository.save(existing);

        log.info(
                "Baggage policy updated successfully baggagePolicyId={}",
                saved.getId()
        );

        return BaggagePolicyMapper.toResponse(saved);
    }


    /**
     * Deletes an existing baggage policy.
     *
     * The entity is loaded before deletion so that a meaningful
     * EntityNotFoundException can be returned when the requested
     * baggage-policy ID does not exist.
     *
     * @param id baggage-policy ID
     */
    @Override
    @Transactional
    public void deleteBaggagePolicy(Long id) {

        log.info(
                "Deleting baggage policy baggagePolicyId={}",
                id
        );

        BaggagePolicy policy =
                baggagePolicyRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete baggage policy because record was not found baggagePolicyId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Baggage policy not found with id: " + id
                            );
                        });

        baggagePolicyRepository.delete(policy);

        log.info(
                "Baggage policy deleted successfully baggagePolicyId={}",
                id
        );
    }
}