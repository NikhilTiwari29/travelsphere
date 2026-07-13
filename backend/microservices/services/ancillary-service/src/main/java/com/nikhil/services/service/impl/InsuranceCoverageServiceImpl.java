package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.InsuranceCoverageRequest;
import com.nikhil.common_lib.payload.response.InsuranceCoverageResponse;
import com.nikhil.services.mapper.InsuranceCoverageMapper;
import com.nikhil.services.model.Ancillary;
import com.nikhil.services.model.InsuranceCoverage;
import com.nikhil.services.repository.AncillaryRepository;
import com.nikhil.services.repository.InsuranceCoverageRepository;
import com.nikhil.services.service.InsuranceCoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation responsible for managing insurance coverage
 * options associated with travel-protection ancillary products.
 *
 * Domain relationship:
 *
 * Ancillary (TRAVEL_PROTECTION)
 *          ↓
 * InsuranceCoverage
 *          ├── Trip Cancellation
 *          ├── Medical Emergency
 *          ├── Baggage Loss
 *          └── Travel Delay
 *
 * An insurance ancillary can contain multiple coverage options describing
 * the individual protection benefits included in the product.
 *
 * Main responsibilities:
 *   - Create a single insurance coverage
 *   - Create multiple coverages for one ancillary in bulk
 *   - Update existing coverage definitions
 *   - Delete coverage definitions
 *   - Retrieve coverage by ID
 *   - Retrieve coverages belonging to an ancillary
 *   - Retrieve active coverage options
 *   - Retrieve all coverage definitions
 *
 * Transaction Strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, bulk create, update, and delete operations explicitly
 *     override the class-level configuration with writable transactions.
 *
 * Persistence:
 *   - All operations use the local pricing-service database.
 *   - No Feign or other remote service calls are required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsuranceCoverageServiceImpl
        implements InsuranceCoverageService {

    private final InsuranceCoverageRepository coverageRepository;
    private final AncillaryRepository ancillaryRepository;


    /**
     * Creates an insurance coverage option for an existing ancillary.
     *
     * Processing flow:
     *
     * InsuranceCoverageRequest
     *          ↓
     * Validate Ancillary existence
     *          ↓
     * Map request to InsuranceCoverage
     *          ↓
     * Persist coverage
     *          ↓
     * Return InsuranceCoverageResponse
     *
     * @param request insurance coverage creation request
     * @return created insurance coverage
     * @throws ResourceNotFoundException if the referenced ancillary
     *                                   does not exist
     */
    @Override
    @Transactional
    public InsuranceCoverageResponse createCoverage(
            InsuranceCoverageRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating insurance coverage ancillaryId={}",
                request.getAncillaryId()
        );

        /*
         * Validate the parent Ancillary before establishing the
         * InsuranceCoverage relationship.
         */
        Ancillary ancillary =
                ancillaryRepository.findById(request.getAncillaryId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot create insurance coverage because ancillary was not found ancillaryId={}",
                                    request.getAncillaryId()
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with ID: "
                                            + request.getAncillaryId()
                            );
                        });

        /*
         * Convert the incoming request into an InsuranceCoverage entity
         * and associate it with the validated Ancillary.
         */
        InsuranceCoverage coverage =
                InsuranceCoverageMapper.toEntity(
                        request,
                        ancillary
                );

        InsuranceCoverage saved =
                coverageRepository.save(coverage);

        log.info(
                "Insurance coverage created successfully coverageId={} ancillaryId={}",
                saved.getId(),
                request.getAncillaryId()
        );

        return InsuranceCoverageMapper.toResponse(saved);
    }


    /**
     * Creates multiple insurance coverage options for a single ancillary.
     *
     * All requests in the bulk payload must belong to the same Ancillary.
     * This constraint keeps the operation scoped to one insurance product
     * and allows the parent Ancillary to be fetched only once.
     *
     * Processing flow:
     *
     * List<InsuranceCoverageRequest>
     *              ↓
     * Validate non-empty request
     *              ↓
     * Extract Ancillary ID
     *              ↓
     * Verify all requests use same Ancillary ID
     *              ↓
     * Validate Ancillary existence
     *              ↓
     * Map all requests to entities
     *              ↓
     * saveAll()
     *              ↓
     * Return created responses
     *
     * The complete bulk operation executes inside one transaction.
     * Any validation or persistence failure rolls back the operation.
     *
     * @param requests insurance coverage creation requests
     * @return created insurance coverage responses
     * @throws ResourceNotFoundException if the referenced ancillary
     *                                   does not exist
     */
    @Override
    @Transactional
    public List<InsuranceCoverageResponse> createCoveragesBulk(
            List<InsuranceCoverageRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Starting bulk insurance coverage creation requestedCount={}",
                requests == null ? 0 : requests.size()
        );

        /*
         * Reject null or empty bulk requests before accessing
         * the first request element.
         */
        if (requests == null || requests.isEmpty()) {

            log.warn(
                    "Bulk insurance coverage creation rejected because request list is empty"
            );

            throw new IllegalArgumentException(
                    "Coverage request list cannot be empty"
            );
        }

        /*
         * The first request establishes the Ancillary ID for the
         * complete bulk operation.
         */
        Long ancillaryId =
                requests.get(0).getAncillaryId();

        /*
         * Verify that every coverage in the bulk request belongs
         * to the same Ancillary.
         */
        boolean allSameAncillary =
                requests.stream()
                        .allMatch(
                                request ->
                                        ancillaryId.equals(
                                                request.getAncillaryId()
                                        )
                        );

        if (!allSameAncillary) {

            log.warn(
                    "Bulk insurance coverage creation rejected because multiple ancillary IDs were provided"
            );

            throw new IllegalArgumentException(
                    "All coverages in bulk request must belong to the same ancillary"
            );
        }

        log.debug(
                "Validated common ancillary for bulk coverage creation ancillaryId={} requestedCount={}",
                ancillaryId,
                requests.size()
        );

        /*
         * Fetch the parent Ancillary once and reuse the entity for
         * all InsuranceCoverage records in the bulk operation.
         */
        Ancillary ancillary =
                ancillaryRepository.findById(ancillaryId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Bulk coverage creation failed because ancillary was not found ancillaryId={}",
                                    ancillaryId
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with ID: "
                                            + ancillaryId
                            );
                        });

        /*
         * Convert all incoming requests into InsuranceCoverage entities.
         */
        List<InsuranceCoverage> coverages =
                requests.stream()
                        .map(
                                request ->
                                        InsuranceCoverageMapper.toEntity(
                                                request,
                                                ancillary
                                        )
                        )
                        .toList();

        /*
         * Persist all coverage entities within the same transaction.
         */
        List<InsuranceCoverage> savedCoverages =
                coverageRepository.saveAll(coverages);

        List<InsuranceCoverageResponse> responses =
                savedCoverages.stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.info(
                "Bulk insurance coverage creation completed successfully ancillaryId={} createdCount={}",
                ancillaryId,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates an existing insurance coverage definition.
     *
     * If ancillaryId is supplied in the request, the referenced Ancillary
     * is validated before being passed to the mapper.
     *
     * Processing flow:
     *
     * coverageId
     *      ↓
     * Fetch InsuranceCoverage
     *      ↓
     * Validate Ancillary if supplied
     *      ↓
     * Apply request values
     *      ↓
     * Persist changes
     *      ↓
     * Return updated response
     *
     * @param id coverage ID
     * @param request updated coverage values
     * @return updated insurance coverage
     * @throws ResourceNotFoundException if the coverage or requested
     *                                   ancillary does not exist
     */
    @Override
    @Transactional
    public InsuranceCoverageResponse updateCoverage(
            Long id,
            InsuranceCoverageRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating insurance coverage coverageId={} requestedAncillaryId={}",
                id,
                request.getAncillaryId()
        );

        InsuranceCoverage existing =
                coverageRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update insurance coverage because record was not found coverageId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Insurance coverage not found with ID: "
                                            + id
                            );
                        });

        Ancillary ancillary = null;

        /*
         * Resolve the Ancillary only when ancillaryId is supplied.
         *
         * A null value allows the mapper to preserve the current
         * Ancillary relationship.
         */
        if (request.getAncillaryId() != null) {

            ancillary =
                    ancillaryRepository
                            .findById(request.getAncillaryId())
                            .orElseThrow(() -> {

                                log.warn(
                                        "Cannot update insurance coverage because requested ancillary was not found coverageId={} ancillaryId={}",
                                        id,
                                        request.getAncillaryId()
                                );

                                return new ResourceNotFoundException(
                                        "Ancillary not found with ID: "
                                                + request.getAncillaryId()
                                );
                            });
        }

        /*
         * Apply incoming request values to the managed entity.
         */
        InsuranceCoverageMapper.updateEntityFromRequest(
                existing,
                request,
                ancillary
        );

        /*
         * Explicit save is retained for clarity.
         *
         * Since 'existing' is a managed entity inside a writable transaction,
         * Hibernate dirty checking would also persist the changes when the
         * transaction commits.
         */
        InsuranceCoverage updated =
                coverageRepository.save(existing);

        log.info(
                "Insurance coverage updated successfully coverageId={} ancillaryId={}",
                updated.getId(),
                updated.getAncillary().getId()
        );

        return InsuranceCoverageMapper.toResponse(updated);
    }


    /**
     * Deletes an insurance coverage definition.
     *
     * The coverage is loaded before deletion so that a meaningful
     * ResourceNotFoundException is returned for an invalid ID.
     *
     * @param id coverage ID
     * @throws ResourceNotFoundException if the coverage does not exist
     */
    @Override
    @Transactional
    public void deleteCoverage(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Deleting insurance coverage coverageId={}",
                id
        );

        InsuranceCoverage coverage =
                coverageRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete insurance coverage because record was not found coverageId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Insurance coverage not found with ID: "
                                            + id
                            );
                        });

        coverageRepository.delete(coverage);

        log.info(
                "Insurance coverage deleted successfully coverageId={}",
                id
        );
    }


    /**
     * Retrieves an insurance coverage by its unique identifier.
     *
     * @param id coverage ID
     * @return insurance coverage details
     * @throws ResourceNotFoundException if the coverage does not exist
     */
    @Override
    public InsuranceCoverageResponse getCoverageById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching insurance coverage coverageId={}",
                id
        );

        InsuranceCoverage coverage =
                coverageRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Insurance coverage not found coverageId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Insurance coverage not found with ID: "
                                            + id
                            );
                        });

        log.debug(
                "Insurance coverage fetched successfully coverageId={}",
                id
        );

        return InsuranceCoverageMapper.toResponse(coverage);
    }


    /**
     * Retrieves coverage options associated with an Ancillary.
     *
     * NOTE:
     * The current repository method returns only active coverages.
     * If this method is intended to return both active and inactive records,
     * use a repository method such as findByAncillaryId(ancillaryId).
     *
     * @param ancillaryId ancillary ID
     * @return coverage options associated with the ancillary
     */
    @Override
    public List<InsuranceCoverageResponse> getCoveragesByAncillaryId(
            Long ancillaryId
    ) {

        log.debug(
                "Fetching insurance coverages ancillaryId={}",
                ancillaryId
        );

        List<InsuranceCoverageResponse> responses =
                coverageRepository
                        .findByAncillaryIdAndActiveTrue(ancillaryId)
                        .stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.debug(
                "Insurance coverages fetched successfully ancillaryId={} count={}",
                ancillaryId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves only active insurance coverage options for an Ancillary.
     *
     * This method is suitable for customer-facing booking flows where
     * inactive coverage definitions must not be offered for purchase.
     *
     * @param ancillaryId ancillary ID
     * @return active insurance coverage options
     */
    @Override
    public List<InsuranceCoverageResponse> getActiveCoveragesByAncillaryId(
            Long ancillaryId
    ) {

        log.debug(
                "Fetching active insurance coverages ancillaryId={}",
                ancillaryId
        );

        List<InsuranceCoverageResponse> responses =
                coverageRepository
                        .findByAncillaryIdAndActiveTrue(ancillaryId)
                        .stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.debug(
                "Active insurance coverages fetched successfully ancillaryId={} count={}",
                ancillaryId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves all insurance coverage definitions.
     *
     * This operation returns both active and inactive records and is
     * primarily suitable for administrative or management interfaces.
     *
     * @return all insurance coverage definitions
     */
    @Override
    public List<InsuranceCoverageResponse> getAllCoverages() {

        log.debug(
                "Fetching all insurance coverages"
        );

        List<InsuranceCoverageResponse> responses =
                coverageRepository.findAll()
                        .stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.debug(
                "All insurance coverages fetched successfully count={}",
                responses.size()
        );

        return responses;
    }
}