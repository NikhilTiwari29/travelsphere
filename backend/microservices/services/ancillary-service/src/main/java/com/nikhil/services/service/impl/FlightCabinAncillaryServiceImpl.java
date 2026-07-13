package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightCabinAncillaryRequest;
import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;
import com.nikhil.common_lib.payload.response.InsuranceCoverageResponse;
import com.nikhil.services.mapper.FlightCabinAncillaryMapper;
import com.nikhil.services.mapper.InsuranceCoverageMapper;
import com.nikhil.services.model.Ancillary;
import com.nikhil.services.model.FlightCabinAncillary;
import com.nikhil.services.model.InsuranceCoverage;
import com.nikhil.services.repository.AncillaryRepository;
import com.nikhil.services.repository.FlightCabinAncillaryRepository;
import com.nikhil.services.repository.InsuranceCoverageRepository;
import com.nikhil.services.service.FlightCabinAncillaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation for managing ancillary offerings configured for
 * flight and cabin-class combinations.
 *
 * FlightCabinAncillary represents the sellable configuration of an ancillary
 * product for a specific flight and cabin class. The configuration maintains
 * offering-level attributes such as price, currency, availability, quantity
 * limits, and fare inclusion.
 *
 * Domain relationship:
 *
 * Flight
 *    +
 * Cabin Class
 *    +
 * Ancillary
 *       ↓
 * FlightCabinAncillary
 *
 * Cross-service references:
 *   - flightId references a Flight owned by flight-ops-service.
 *   - cabinClassId references a CabinClass owned by seat-service.
 *   - Ancillary and InsuranceCoverage are managed locally.
 *
 * Transaction strategy:
 *   - Read operations use the class-level read-only transaction.
 *   - Create, bulk create, update, and delete operations explicitly use
 *     writable transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightCabinAncillaryServiceImpl
        implements FlightCabinAncillaryService {

    private final FlightCabinAncillaryRepository repository;
    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;


    /**
     * Maps a FlightCabinAncillary entity to its API response and enriches
     * the response with coverage definitions associated with the ancillary.
     */
    private FlightCabinAncillaryResponse mapWithCoverages(
            FlightCabinAncillary entity
    ) {

        List<InsuranceCoverage> coverages =
                insuranceCoverageRepository.findByAncillary(
                        entity.getAncillary()
                );

        List<InsuranceCoverageResponse> coverageResponses =
                coverages.stream()
                        .map(InsuranceCoverageMapper::toResponse)
                        .toList();

        return FlightCabinAncillaryMapper.toResponse(
                entity,
                coverageResponses
        );
    }


    /**
     * Creates an ancillary offering for a flight and cabin-class combination.
     *
     * The referenced Ancillary is validated locally before the offering
     * configuration is persisted.
     */
    @Override
    @Transactional
    public FlightCabinAncillaryResponse create(
            FlightCabinAncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Creating flight cabin ancillary flightId={} cabinClassId={} ancillaryId={}",
                request.getFlightId(),
                request.getCabinClassId(),
                request.getAncillaryId()
        );

        Ancillary ancillary =
                ancillaryRepository.findById(request.getAncillaryId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot create flight cabin ancillary because ancillary was not found ancillaryId={} flightId={} cabinClassId={}",
                                    request.getAncillaryId(),
                                    request.getFlightId(),
                                    request.getCabinClassId()
                            );

                            return new ResourceNotFoundException(
                                    "Ancillary not found with id: "
                                            + request.getAncillaryId()
                            );
                        });

        FlightCabinAncillary entity =
                buildEntity(request, ancillary);

        FlightCabinAncillary saved =
                repository.save(entity);

        log.info(
                "Flight cabin ancillary created successfully id={} flightId={} cabinClassId={} ancillaryId={}",
                saved.getId(),
                saved.getFlightId(),
                saved.getCabinClassId(),
                ancillary.getId()
        );

        return mapWithCoverages(saved);
    }


    /**
     * Creates multiple flight-cabin ancillary offerings in one transaction.
     *
     * Every referenced Ancillary must exist. Any validation or persistence
     * failure rolls back the complete bulk operation.
     */
    @Override
    @Transactional
    public List<FlightCabinAncillaryResponse> bulkCreate(
            List<FlightCabinAncillaryRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Starting bulk flight cabin ancillary creation requestedCount={}",
                requests.size()
        );

        List<FlightCabinAncillary> entities =
                requests.stream()
                        .map(request -> {

                            Ancillary ancillary =
                                    null;
                            try {
                                ancillary = ancillaryRepository
                                        .findById(request.getAncillaryId())
                                        .orElseThrow(() -> {

                                            log.warn(
                                                    "Bulk creation failed because ancillary was not found ancillaryId={} flightId={} cabinClassId={}",
                                                    request.getAncillaryId(),
                                                    request.getFlightId(),
                                                    request.getCabinClassId()
                                            );

                                            return new ResourceNotFoundException(
                                                    "Ancillary not found with id: "
                                                            + request.getAncillaryId()
                                            );
                                        });
                            } catch (ResourceNotFoundException e) {
                                throw new RuntimeException(e);
                            }

                            return buildEntity(
                                    request,
                                    ancillary
                            );
                        })
                        .toList();

        /*
         * Persist the validated batch in one repository operation within
         * the current transaction.
         */
        List<FlightCabinAncillary> savedEntities =
                repository.saveAll(entities);

        List<FlightCabinAncillaryResponse> responses =
                savedEntities.stream()
                        .map(this::mapWithCoverages)
                        .toList();

        log.info(
                "Bulk flight cabin ancillary creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves a flight-cabin ancillary offering by its identifier.
     */
    @Override
    public FlightCabinAncillaryResponse getById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching flight cabin ancillary id={}",
                id
        );

        FlightCabinAncillary entity =
                repository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight cabin ancillary not found id={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightCabinAncillary not found with id: "
                                            + id
                            );
                        });

        FlightCabinAncillaryResponse response =
                mapWithCoverages(entity);

        log.debug(
                "Flight cabin ancillary fetched successfully id={}",
                id
        );

        return response;
    }


    /**
     * Retrieves all ancillary offerings configured for a flight and
     * cabin-class combination.
     */
    @Override
    public List<FlightCabinAncillaryResponse>
    getAllByFlightAndCabinClass(
            Long flightId,
            Long cabinClassId
    ) {

        log.debug(
                "Fetching flight cabin ancillaries flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        List<FlightCabinAncillaryResponse> responses =
                repository
                        .findByFlightIdAndCabinClassId(
                                flightId,
                                cabinClassId
                        )
                        .stream()
                        .map(this::mapWithCoverages)
                        .toList();

        log.debug(
                "Flight cabin ancillaries fetched successfully flightId={} cabinClassId={} count={}",
                flightId,
                cabinClassId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves multiple flight-cabin ancillary offerings by identifier.
     *
     * Missing identifiers are not returned by findAllById. Callers requiring
     * strict all-or-nothing resolution should validate the result count.
     */
    @Override
    public List<FlightCabinAncillaryResponse> getAllByIds(
            List<Long> ids
    ) {

        log.debug(
                "Fetching flight cabin ancillaries by IDs requestedCount={}",
                ids.size()
        );

        List<FlightCabinAncillary> entities =
                repository.findAllById(ids);

        List<FlightCabinAncillaryResponse> responses =
                entities.stream()
                        .map(this::mapWithCoverages)
                        .toList();

        log.debug(
                "Flight cabin ancillaries fetched by IDs requestedCount={} resultCount={}",
                ids.size(),
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves a single ancillary offering for the specified flight,
     * cabin class, and ancillary type.
     *
     * This operation assumes that the repository query can resolve at most
     * one offering for the supplied combination.
     */
    @Override
    public FlightCabinAncillaryResponse
    getByFlightIdAndCabinClassAndType(
            Long flightId,
            Long cabinClassId,
            AncillaryType type
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching flight cabin ancillary flightId={} cabinClassId={} type={}",
                flightId,
                cabinClassId,
                type
        );

        FlightCabinAncillary entity =
                repository
                        .findByFlightIdAndCabinClassIdAndAncillary_Type(
                                flightId,
                                cabinClassId,
                                type
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Flight cabin ancillary not found flightId={} cabinClassId={} type={}",
                                    flightId,
                                    cabinClassId,
                                    type
                            );

                            return new ResourceNotFoundException(
                                    "FlightCabinAncillary not found for type: "
                                            + type
                            );
                        });

        return mapWithCoverages(entity);
    }


    /**
     * Retrieves all ancillary offerings of a specific type configured for
     * the supplied flight and cabin class.
     */
    @Override
    public List<FlightCabinAncillaryResponse>
    getAllByFlightIdAndCabinClassAndType(
            Long flightId,
            Long cabinClassId,
            AncillaryType type
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching all flight cabin ancillaries by type flightId={} cabinClassId={} type={}",
                flightId,
                cabinClassId,
                type
        );

        List<FlightCabinAncillary> entities =
                repository
                        .findAllByFlightIdAndCabinClassIdAndAncillary_Type(
                                flightId,
                                cabinClassId,
                                type
                        );

        List<FlightCabinAncillaryResponse> responses =
                entities.stream()
                        .map(this::mapWithCoverages)
                        .toList();

        log.debug(
                "Flight cabin ancillaries fetched by type flightId={} cabinClassId={} type={} count={}",
                flightId,
                cabinClassId,
                type,
                responses.size()
        );

        return responses;
    }


    /**
     * Updates the commercial configuration of an existing ancillary offering.
     *
     * The current implementation updates mutable commercial attributes only.
     * Flight, cabin class, and ancillary relationships remain unchanged.
     */
    @Override
    @Transactional
    public FlightCabinAncillaryResponse update(
            Long id,
            FlightCabinAncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating flight cabin ancillary id={}",
                id
        );

        FlightCabinAncillary entity =
                repository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update flight cabin ancillary because record was not found id={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightCabinAncillary not found with id: "
                                            + id
                            );
                        });

        entity.setAvailable(request.getAvailable());
        entity.setMaxQuantity(request.getMaxQuantity());
        entity.setPrice(request.getPrice());
        entity.setCurrency(request.getCurrency());
        entity.setIncludedInFare(request.getIncludedInFare());

        /*
         * Explicit save is retained for command-operation clarity. Because
         * the entity is managed in a writable transaction, JPA dirty checking
         * would also persist the changes at commit time.
         */
        FlightCabinAncillary updated =
                repository.save(entity);

        log.info(
                "Flight cabin ancillary updated successfully id={} flightId={} cabinClassId={} ancillaryId={}",
                updated.getId(),
                updated.getFlightId(),
                updated.getCabinClassId(),
                updated.getAncillary().getId()
        );

        return mapWithCoverages(updated);
    }


    /**
     * Deletes a flight-cabin ancillary offering.
     *
     * The entity is loaded before deletion to provide consistent not-found
     * handling instead of relying on deleteById behavior.
     */
    @Override
    @Transactional
    public void delete(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Deleting flight cabin ancillary id={}",
                id
        );

        FlightCabinAncillary entity =
                repository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete flight cabin ancillary because record was not found id={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "FlightCabinAncillary not found with id: "
                                            + id
                            );
                        });

        repository.delete(entity);

        log.info(
                "Flight cabin ancillary deleted successfully id={}",
                id
        );
    }


    /**
     * Calculates the aggregate price of selected flight-cabin ancillary
     * offerings.
     *
     * Pricing is resolved from FlightCabinAncillary records because the
     * sellable price belongs to the flight and cabin-specific configuration,
     * not the reusable Ancillary catalog definition.
     */
    @Override
    public Double calculateAncillaryPrice(
            List<Long> ancillaryIds
    ) {

        log.debug(
                "Calculating ancillary price total requestedCount={}",
                ancillaryIds.size()
        );

        List<FlightCabinAncillary> ancillaries =
                repository.findAllById(ancillaryIds);

        double totalPrice =
                ancillaries.stream()
                        .mapToDouble(
                                FlightCabinAncillary::getPrice
                        )
                        .sum();

        log.debug(
                "Ancillary price calculation completed requestedCount={} resolvedCount={} totalPrice={}",
                ancillaryIds.size(),
                ancillaries.size(),
                totalPrice
        );

        return totalPrice;
    }


    /**
     * Creates a FlightCabinAncillary entity from a validated request and
     * resolved Ancillary reference.
     */
    private FlightCabinAncillary buildEntity(
            FlightCabinAncillaryRequest request,
            Ancillary ancillary
    ) {

        return FlightCabinAncillary.builder()
                .flightId(request.getFlightId())
                .cabinClassId(request.getCabinClassId())
                .ancillary(ancillary)
                .available(request.getAvailable())
                .maxQuantity(request.getMaxQuantity())
                .price(request.getPrice())
                .currency(request.getCurrency())
                .includedInFare(request.getIncludedInFare())
                .build();
    }
}