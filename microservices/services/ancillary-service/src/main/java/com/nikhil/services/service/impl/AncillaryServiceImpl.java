package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AncillaryRequest;
import com.nikhil.common_lib.payload.response.AncillaryResponse;
import com.nikhil.common_lib.payload.response.InsuranceCoverageResponse;
import com.nikhil.services.Integration.AirlineIntegrationService;
import com.nikhil.services.mapper.AncillaryMapper;
import com.nikhil.services.mapper.InsuranceCoverageMapper;
import com.nikhil.services.model.Ancillary;
import com.nikhil.services.model.InsuranceCoverage;
import com.nikhil.services.repository.AncillaryRepository;
import com.nikhil.services.repository.InsuranceCoverageRepository;
import com.nikhil.services.service.AncillaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation responsible for managing airline-scoped
 * ancillary product definitions.
 *
 * Ancillaries represent optional products or services that can be offered
 * in addition to the base flight fare.
 *
 * Examples:
 *   - Travel insurance
 *   - Lounge access
 *   - Priority services
 *   - Airport transfers
 *   - Extra baggage products
 *   - Meal packages
 *   - Wi-Fi packages
 *
 * Ownership model:
 *
 * User
 *   ↓
 * AirlineIntegrationService
 *   ↓
 * Airline Core Service (Feign)
 *   ↓
 * Airline ID
 *   ↓
 * Ancillary
 *
 * Insurance relationship:
 *
 * Ancillary
 *     ↓
 * InsuranceCoverage
 *
 * Insurance-type ancillaries can contain one or more coverage definitions.
 * The service enriches AncillaryResponse with coverage details when reading
 * or updating ancillary records.
 *
 * Main responsibilities:
 *   - Resolve Airline ID from the authenticated user
 *   - Create airline-owned ancillary definitions
 *   - Retrieve ancillary details
 *   - Enrich responses with insurance coverage information
 *   - Retrieve all ancillary products for an airline
 *   - Update existing ancillary definitions
 *   - Delete ancillary definitions
 *
 * Transaction Strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, update, and delete operations explicitly override the
 *     class-level transaction with writable transactions.
 *
 * Integration:
 *   - AirlineIntegrationService performs the external airline ownership lookup.
 *   - Ancillary and InsuranceCoverage persistence operations use the local
 *     pricing-service database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AncillaryServiceImpl implements AncillaryService {

    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;
    private final AirlineIntegrationService airlineIntegrationService;


    /**
     * Creates an ancillary product for the airline associated with the
     * authenticated user.
     *
     * Processing flow:
     *
     * userId
     *    ↓
     * Resolve Airline ID through AirlineIntegrationService
     *    ↓
     * Build Ancillary entity
     *    ↓
     * Persist Ancillary
     *    ↓
     * Map to AncillaryResponse
     *
     * Insurance coverage records are not created in this operation.
     * Therefore, the response is initially mapped without coverage details.
     *
     * @param userId authenticated user ID
     * @param request ancillary creation request
     * @return created ancillary product
     * @throws ResourceNotFoundException if the airline associated with the
     *                                   user cannot be resolved
     */
    @Override
    @Transactional
    public AncillaryResponse create(
            Long userId,
            AncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating ancillary for authenticated user userId={}",
                userId
        );


        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        log.debug(
                "Resolved airline for ancillary creation userId={} airlineId={}",
                userId,
                airlineId
        );

        /*
         * Build the Ancillary entity from the incoming request.
         *
         * airlineId is resolved from the authenticated user rather than
         * accepted from the client request, preventing the caller from
         * assigning an ancillary to an arbitrary airline.
         */
        Ancillary ancillary = Ancillary.builder()
                .type(request.getType())
                .subType(request.getSubType())
                .rfisc(request.getRfisc())
                .name(request.getName())
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .displayOrder(request.getDisplayOrder())
                .airlineId(airlineId)
                .build();

        Ancillary saved =
                ancillaryRepository.save(ancillary);

        log.info(
                "Ancillary created successfully ancillaryId={} airlineId={}",
                saved.getId(),
                airlineId
        );

        /*
         * Insurance coverage records are managed separately.
         * Therefore, no coverage list is attached during initial creation.
         */
        return AncillaryMapper.toResponse(saved, null);
    }


    /**
     * Retrieves an ancillary product by its unique identifier.
     *
     * The ancillary response is enriched with any InsuranceCoverage records
     * associated with the ancillary.
     *
     * Processing flow:
     *
     * ancillaryId
     *      ↓
     * Fetch Ancillary
     *      ↓
     * Fetch InsuranceCoverage records
     *      ↓
     * Map coverage entities to responses
     *      ↓
     * Build enriched AncillaryResponse
     *
     * @param id ancillary ID
     * @return ancillary details including insurance coverage information
     * @throws ResourceNotFoundException if the ancillary does not exist
     */
    @Override
    public AncillaryResponse getById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching ancillary ancillaryId={}",
                id
        );

        Ancillary ancillary =
                ancillaryRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Ancillary not found ancillaryId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with id: " + id
                            );
                        });

        /*
         * Fetch insurance coverage definitions associated with the ancillary.
         *
         * For non-insurance ancillary types, this list is expected to be empty.
         */
        List<InsuranceCoverage> insuranceCoverages =
                insuranceCoverageRepository.findByAncillary(ancillary);

        List<InsuranceCoverageResponse> coverageResponseList =
                insuranceCoverages.stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.debug(
                "Ancillary fetched successfully ancillaryId={} coverageCount={}",
                id,
                coverageResponseList.size()
        );

        return AncillaryMapper.toResponse(
                ancillary,
                coverageResponseList
        );
    }


    /**
     * Retrieves all ancillary products belonging to the airline associated
     * with the authenticated user.
     *
     * Processing flow:
     *
     * userId
     *    ↓
     * Resolve Airline ID
     *    ↓
     * Fetch airline ancillaries
     *    ↓
     * Fetch insurance coverage for each ancillary
     *    ↓
     * Build enriched AncillaryResponse list
     *
     * @param userId authenticated user ID
     * @return ancillary products belonging to the user's airline
     */
    @Override
    public List<AncillaryResponse> getAllByAirlineId(
            Long userId
    ) {

        log.debug(
                "Fetching ancillaries for authenticated user userId={}",
                userId
        );

        /*
         * Resolve airline ownership from the authenticated user.
         */
        Long airlineId =
                airlineIntegrationService.getAirlineIdForUser(userId);

        log.debug(
                "Resolved airline for ancillary lookup userId={} airlineId={}",
                userId,
                airlineId
        );

        List<Ancillary> ancillaries =
                ancillaryRepository.findByAirlineId(airlineId);

        /*
         * Enrich every Ancillary with its InsuranceCoverage definitions.
         *
         * Note:
         * This currently executes one insurance-coverage query per ancillary.
         * For a small ancillary catalog this may be acceptable, but for larger
         * result sets this should be replaced with a batch-fetch strategy.
         */
        List<AncillaryResponse> responses =
                ancillaries.stream()
                        .map(ancillary -> {

                            List<InsuranceCoverage> insuranceCoverages =
                                    insuranceCoverageRepository
                                            .findByAncillary(ancillary);

                            List<InsuranceCoverageResponse> coverageResponseList =
                                    insuranceCoverages.stream()
                                            .map(InsuranceCoverageMapper::toResponse)
                                            .toList();

                            return AncillaryMapper.toResponse(
                                    ancillary,
                                    coverageResponseList
                            );
                        })
                        .toList();

        log.debug(
                "Ancillaries fetched successfully userId={} airlineId={} count={}",
                userId,
                airlineId,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates an existing ancillary product.
     *
     * Processing flow:
     *
     * ancillaryId
     *      ↓
     * Fetch existing Ancillary
     *      ↓
     * Apply request values
     *      ↓
     * Persist changes
     *      ↓
     * Fetch InsuranceCoverage records
     *      ↓
     * Return enriched AncillaryResponse
     *
     * Because the entity is loaded within a writable transaction, it becomes
     * managed by the persistence context. Hibernate dirty checking can persist
     * the changes automatically at transaction commit.
     *
     * @param id ancillary ID
     * @param request updated ancillary values
     * @return updated ancillary details including insurance coverage
     * @throws ResourceNotFoundException if the ancillary does not exist
     */
    @Override
    @Transactional
    public AncillaryResponse update(
            Long id,
            AncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating ancillary ancillaryId={}",
                id
        );

        Ancillary ancillary =
                ancillaryRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update ancillary because record was not found ancillaryId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with id: " + id
                            );
                        });

        /*
         * Apply mutable fields from the request to the existing entity.
         *
         * airlineId is intentionally not changed here because ancillary
         * ownership should not be transferable through a normal update.
         */
        ancillary.setType(request.getType());
        ancillary.setSubType(request.getSubType());
        ancillary.setRfisc(request.getRfisc());
        ancillary.setName(request.getName());
        ancillary.setDescription(request.getDescription());
        ancillary.setMetadata(request.getMetadata());
        ancillary.setDisplayOrder(request.getDisplayOrder());

        /*
         * Explicit save is retained for clarity.
         *
         * Since ancillary is already a managed entity inside a writable
         * transaction, Hibernate dirty checking would also persist these
         * modifications automatically when the transaction commits.
         */
        Ancillary updated =
                ancillaryRepository.save(ancillary);

        /*
         * Fetch coverage definitions so that the update response contains
         * the same enriched structure as the read operation.
         */
        List<InsuranceCoverage> insuranceCoverages =
                insuranceCoverageRepository.findByAncillary(ancillary);

        List<InsuranceCoverageResponse> coverageResponseList =
                insuranceCoverages.stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        log.info(
                "Ancillary updated successfully ancillaryId={} coverageCount={}",
                updated.getId(),
                coverageResponseList.size()
        );

        return AncillaryMapper.toResponse(
                updated,
                coverageResponseList
        );
    }


    /**
     * Deletes an existing ancillary product.
     *
     * The entity is loaded before deletion so that a meaningful
     * ResourceNotFoundException can be returned when the requested
     * ancillary ID does not exist.
     *
     * Any dependent InsuranceCoverage deletion behavior depends on the
     * configured JPA cascade and database foreign-key rules.
     *
     * @param id ancillary ID
     */
    @Override
    @Transactional
    public void delete(Long id) throws ResourceNotFoundException {

        log.info(
                "Deleting ancillary ancillaryId={}",
                id
        );

        /*
         * Load the entity explicitly instead of calling deleteById()
         * directly so that missing resources are handled consistently.
         */
        Ancillary ancillary =
                ancillaryRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete ancillary because record was not found ancillaryId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with id: " + id
                            );
                        });

        ancillaryRepository.delete(ancillary);

        log.info(
                "Ancillary deleted successfully ancillaryId={}",
                id
        );
    }
}