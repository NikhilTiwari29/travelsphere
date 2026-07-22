package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.services.exception.FareAlreadyExistsException;
import com.nikhil.services.exception.FareNotFoundException;
import com.nikhil.services.mapper.FareMapper;
import com.nikhil.services.model.Fare;
import com.nikhil.services.repository.FareRepository;
import com.nikhil.services.service.FareService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * Handles fare creation, retrieval, updates, deletion, and price searches.
 *
 * Pricing Service stores Flight and CabinClass IDs as cross-service references
 * and provides pricing APIs used by Flight Ops and Booking services.
 *
 * Read operations use read-only transactions by default. Methods that modify
 * fare data explicitly override the class-level transaction configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FareServiceImpl implements FareService {

    private final FareRepository fareRepository;


    // ==================== Create Operations ====================

    /**
     * Creates a fare after ensuring the fare name is unique for the
     * requested Flight and CabinClass combination.
     */
    @Override
    @Transactional
    public FareResponse createFare(
            FareRequest request
    ) {

        log.info(
                "Creating fare flightId={} cabinClassId={} fareName={}",
                request.getFlightId(),
                request.getCabinClassId(),
                request.getName()
        );

        /*
         * Prevent duplicate fare products for the same Flight,
         * CabinClass, and fare name combination.
         */
        if (fareRepository.existsByFlightIdAndCabinClassIdAndName(
                request.getFlightId(),
                request.getCabinClassId(),
                request.getName()
        )) {

            log.warn(
                    "Fare creation rejected: duplicate fare flightId={} cabinClassId={} fareName={}",
                    request.getFlightId(),
                    request.getCabinClassId(),
                    request.getName()
            );

            throw new FareAlreadyExistsException(request.getName());
        }

        Fare fare =
                FareMapper.toEntity(request);

        Fare saved =
                fareRepository.save(fare);

        log.info(
                "Fare created successfully fareId={} flightId={} cabinClassId={}",
                saved.getId(),
                saved.getFlightId(),
                saved.getCabinClassId()
        );

        return FareMapper.toResponse(saved);
    }


    /**
     * Creates multiple fares while skipping fare combinations that
     * already exist in the database.
     *
     * The complete bulk operation runs in one transaction so database
     * changes are committed or rolled back together.
     */
    @Override
    @Transactional
    public List<FareResponse> createFares(
            List<FareRequest> requests
    ) {

        log.info(
                "Bulk fare creation started requestedCount={}",
                requests.size()
        );

        /*
         * Collect unique Flight IDs and load existing fare keys in one
         * database query instead of checking each request individually.
         */
        Set<Long> flightIds =
                requests.stream()
                        .map(FareRequest::getFlightId)
                        .collect(Collectors.toSet());

        Set<String> existingKeys =
                fareRepository.findExistingFareKeys(
                        flightIds
                );

        /*
         * Skip requests whose Flight + CabinClass + Name combination
         * already exists and prepare only new Fare entities for persistence.
         */
        List<Fare> toSave =
                requests.stream()
                        .filter(request -> {

                            String fareKey =
                                    request.getFlightId()
                                            + ":"
                                            + request.getCabinClassId()
                                            + ":"
                                            + request.getName();

                            boolean exists =
                                    existingKeys.contains(fareKey);

                            if (exists) {

                                log.debug(
                                        "Skipping existing fare flightId={} cabinClassId={} fareName={}",
                                        request.getFlightId(),
                                        request.getCabinClassId(),
                                        request.getName()
                                );
                            }

                            return !exists;
                        })
                        .map(FareMapper::toEntity)
                        .collect(Collectors.toList());

        List<Fare> saved =
                fareRepository.saveAll(toSave);

        log.info(
                "Bulk fare creation completed requestedCount={} createdCount={} skippedCount={}",
                requests.size(),
                saved.size(),
                requests.size() - saved.size()
        );

        return saved.stream()
                .map(FareMapper::toResponse)
                .collect(Collectors.toList());
    }


// ==================== Read Operations ====================

    /**
     * Returns a Fare by its database identifier.
     *
     * The response is cached because individual fare lookups may be
     * repeatedly requested by downstream services.
     */
    @Override
    @Cacheable(
            cacheNames = "fares",
            key = "#id"
    )
    public FareResponse getFareById(
            Long id
    ) {

        log.debug(
                "Fetching fare fareId={}",
                id
        );

        Fare fare =
                fareRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Fare lookup failed: fareId={} not found",
                                    id
                            );

                            return new FareNotFoundException(id);
                        });

        log.debug(
                "Fare retrieved successfully fareId={}",
                id
        );

        return FareMapper.toResponse(fare);
    }


    /**
     * Returns all fares configured for the requested Flight
     * and CabinClass combination.
     */
    @Override
    public List<FareResponse> getFaresByFlightIdAndCabinClassId(
            Long flightId,
            Long cabinClassId
    ) {

        log.debug(
                "Fetching fares flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        List<FareResponse> responses =
                fareRepository
                        .findByFlightIdAndCabinClassId(
                                flightId,
                                cabinClassId
                        )
                        .stream()
                        .map(FareMapper::toResponse)
                        .collect(Collectors.toList());

        log.debug(
                "Fare lookup completed flightId={} cabinClassId={} returnedCount={}",
                flightId,
                cabinClassId,
                responses.size()
        );

        return responses;
    }


    /**
     * Returns all configured Fare records.
     */
    @Override
    public List<Fare> getFares() {

        log.debug(
                "Fetching all fares"
        );

        List<Fare> fares =
                fareRepository.findAll();

        log.debug(
                "All fares retrieved returnedCount={}",
                fares.size()
        );

        return fares;
    }


    /**
     * Returns multiple fares by ID using a single database operation.
     *
     * The result is returned as fareId → FareResponse for efficient
     * lookup by downstream services.
     */
    @Override
    public Map<Long, FareResponse> getFaresByIds(
            List<Long> ids
    ) {

        if (ids == null || ids.isEmpty()) {

            log.debug(
                    "Batch fare lookup skipped: no fare IDs provided"
            );

            return Map.of();
        }

        log.debug(
                "Batch fare lookup started requestedCount={}",
                ids.size()
        );

        List<Fare> fares =
                fareRepository.findAllById(ids);

        Map<Long, FareResponse> result =
                fares.stream()
                        .collect(
                                Collectors.toMap(
                                        Fare::getId,
                                        FareMapper::toResponse
                                )
                        );

        log.debug(
                "Batch fare lookup completed requestedCount={} returnedCount={}",
                ids.size(),
                result.size()
        );

        return result;
    }


// ==================== Update Operations ====================

    /**
     * Updates an existing Fare while preserving uniqueness of the
     * Flight + CabinClass + Name combination.
     */
    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(
                            cacheNames = "fares",
                            key = "#id"
                    ),
                    @CacheEvict(
                            cacheNames = "faresByFlight",
                            allEntries = true
                    )
            }
    )
    public FareResponse updateFare(
            Long id,
            FareRequest request
    ) {

        log.info(
                "Updating fare fareId={} flightId={} cabinClassId={} fareName={}",
                id,
                request.getFlightId(),
                request.getCabinClassId(),
                request.getName()
        );

        Fare existing =
                fareRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Fare update failed: fareId={} not found",
                                    id
                            );

                            return new FareNotFoundException(id);
                        });

        /*
         * Exclude the current Fare record from the duplicate check.
         *
         * This allows the Fare to keep its current name while preventing
         * another Fare from using the same Flight + CabinClass + Name key.
         */
        if (fareRepository
                .existsByFlightIdAndCabinClassIdAndNameAndIdNot(
                        request.getFlightId(),
                        request.getCabinClassId(),
                        request.getName(),
                        id
                )) {

            log.warn(
                    "Fare update rejected: duplicate fare fareId={} flightId={} cabinClassId={} fareName={}",
                    id,
                    request.getFlightId(),
                    request.getCabinClassId(),
                    request.getName()
            );

            throw new FareAlreadyExistsException(request.getName());
        }

        FareMapper.updateEntity(
                request,
                existing
        );

        Fare saved =
                fareRepository.save(existing);

        log.info(
                "Fare updated successfully fareId={} flightId={} cabinClassId={}",
                saved.getId(),
                saved.getFlightId(),
                saved.getCabinClassId()
        );

        return FareMapper.toResponse(saved);
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a Fare and removes potentially stale cached fare data.
     */
    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(
                            cacheNames = "fares",
                            key = "#id"
                    ),
                    @CacheEvict(
                            cacheNames = "faresByFlight",
                            allEntries = true
                    )
            }
    )
    public void deleteFare(
            Long id
    ) {

        log.info(
                "Deleting fare fareId={}",
                id
        );

        Fare fare =
                fareRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Fare deletion failed: fareId={} not found",
                                    id
                            );

                            return new FareNotFoundException(id);
                        });

        fareRepository.delete(fare);

        log.info(
                "Fare deleted successfully fareId={}",
                id
        );
    }


// ==================== Pricing Search Operations ====================

    /**
     * Returns the cheapest Fare for each requested Flight within
     * the specified CabinClass.
     *
     * Used by Flight Ops to enrich multiple flight-search results
     * with pricing information through a single batch request.
     */
    @Override
    public Map<Long, FareResponse> getLowestFarePerFlight(
            List<Long> flightIds,
            Long cabinClassId
    ) {

        if (flightIds == null || flightIds.isEmpty()) {

            log.debug(
                    "Lowest fare batch search skipped: no flight IDs provided"
            );

            return Map.of();
        }

        log.debug(
                "Lowest fare batch search started flightCount={} cabinClassId={}",
                flightIds.size(),
                cabinClassId
        );

        /*
         * Load all matching Fare records for the requested Flights and
         * CabinClass in one query instead of querying each Flight separately.
         */
        List<Fare> fares =
                fareRepository.findByFlightIdInAndCabinClassId(
                        flightIds,
                        cabinClassId
                );

        log.debug(
                "Candidate fares loaded requestedFlightCount={} cabinClassId={} candidateFareCount={}",
                flightIds.size(),
                cabinClassId,
                fares.size()
        );

        /*
         * Convert the Fare list into one entry per Flight.
         *
         * If multiple fares exist for the same Flight, the merge function
         * compares their total prices and keeps only the cheaper Fare.
         */
        Map<Long, Fare> lowestFareByFlight =
                fares.stream()
                        .collect(
                                Collectors.toMap(
                                        Fare::getFlightId,
                                        fare -> fare,
                                        (existing, candidate) ->
                                                candidate.getTotalPrice()
                                                        < existing.getTotalPrice()
                                                        ? candidate
                                                        : existing
                                )
                        );

        /*
         * Convert the selected Fare entities into API response objects while
         * preserving the Flight ID as the map key for efficient lookup.
         */
        Map<Long, FareResponse> result =
                lowestFareByFlight
                        .entrySet()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry ->
                                                FareMapper.toResponse(
                                                        entry.getValue()
                                                )
                                )
                        );

        log.debug(
                "Lowest fare batch search completed requestedFlightCount={} matchedFlightCount={} cabinClassId={}",
                flightIds.size(),
                result.size(),
                cabinClassId
        );

        return result;
    }


    /**
     * Returns the cheapest Fare for one Flight and CabinClass combination.
     */
    @Override
    public FareResponse getLowestFareForFlightAndCabin(
            Long flightId,
            Long cabinClassId
    ) {

        log.debug(
                "Lowest fare lookup started flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        List<Fare> fares =
                fareRepository.findByFlightIdAndCabinClassId(
                        flightId,
                        cabinClassId
                );

        /*
         * Compare all Fare products available for the requested Flight
         * and CabinClass and select the one with the lowest total price.
         */
        Fare lowestFare =
                fares.stream()
                        .min(
                                Comparator.comparingDouble(
                                        Fare::getTotalPrice
                                )
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Lowest fare lookup failed: no fare found flightId={} cabinClassId={}",
                                    flightId,
                                    cabinClassId
                            );

                            return new FareNotFoundException(flightId,cabinClassId);
                        });

        log.debug(
                "Lowest fare lookup completed flightId={} cabinClassId={} fareId={} totalPrice={}",
                flightId,
                cabinClassId,
                lowestFare.getId(),
                lowestFare.getTotalPrice()
        );

        return FareMapper.toResponse(lowestFare);
    }
}