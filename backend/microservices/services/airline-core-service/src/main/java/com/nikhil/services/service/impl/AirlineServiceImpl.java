package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.AirlineStatus;
import com.nikhil.common_lib.payload.request.AirlineRequest;
import com.nikhil.common_lib.payload.response.AirlineDropdownItem;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.services.exception.AirlineNotFoundException;
import com.nikhil.services.exception.AirlineOwnershipMismatchException;
import com.nikhil.services.mapper.AirlineMapper;
import com.nikhil.services.model.Airline;
import com.nikhil.services.repository.AirlineRepository;
import com.nikhil.services.service.AirlineService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation for airline management.
 *
 * Handles airline CRUD operations, administrative status transitions,
 * paginated retrieval, dropdown projections, and Redis cache synchronization.
 *
 * Airline ownership is determined using the authenticated user's ID
 * propagated through the X-User-Id request header.
 *
 * Read operations execute in read-only transactions by default. Write
 * operations explicitly override the transaction mode and evict affected
 * cache regions after successful method execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;


    // ==================== CRUD Operations ====================

    /**
     * Creates a new airline associated with the authenticated owner.
     */
    @Override
    @Transactional
    public AirlineResponse createAirline(
            AirlineRequest request,
            Long ownerId
    ) {

        log.info(
                "Creating airline ownerId={} iataCode={} icaoCode={}",
                ownerId,
                request.getIataCode(),
                request.getIcaoCode()
        );

        Airline airline =
                AirlineMapper.toEntity(request, ownerId);

        Airline saved =
                airlineRepository.save(airline);

        log.info(
                "Airline created successfully airlineId={} ownerId={} iataCode={}",
                saved.getId(),
                ownerId,
                saved.getIataCode()
        );

        return AirlineMapper.toResponse(saved);
    }


    /**
     * Returns the airline owned by the authenticated user.
     *
     * The result is cached by owner ID because an owner is associated
     * with a specific airline.
     */
    @Override
    @Cacheable(
            cacheNames = "airlinesByOwner",
            key = "#ownerId"
    )
    public AirlineResponse getAirlineByOwner(Long ownerId) {

        log.debug(
                "Fetching airline by ownerId={}",
                ownerId
        );

        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> {

                    log.warn(
                            "Airline lookup failed: no airline found for ownerId={}",
                            ownerId
                    );

                    return new AirlineNotFoundException(ownerId);
                });

        return AirlineMapper.toResponse(airline);
    }


    /**
     * Returns an airline by its unique database ID.
     */
    @Override
    @Cacheable(
            cacheNames = "airlines",
            key = "#id"
    )
    public AirlineResponse getAirlineById(Long id) {

        log.debug(
                "Fetching airline by airlineId={}",
                id
        );

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> {

                    log.warn(
                            "Airline lookup failed: airlineId={} not found",
                            id
                    );

                    return new AirlineNotFoundException(id);
                });

        return AirlineMapper.toResponse(airline);
    }


    /**
     * Returns a paginated list of airlines.
     */
    @Override
    public Page<AirlineResponse> getAllAirlines(
            Pageable pageable
    ) {

        log.debug(
                "Fetching airlines page={} size={} sort={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        Page<AirlineResponse> result =
                airlineRepository.findAll(pageable)
                        .map(AirlineMapper::toResponse);

        log.debug(
                "Airline page retrieved page={} returnedElements={} totalElements={} totalPages={}",
                result.getNumber(),
                result.getNumberOfElements(),
                result.getTotalElements(),
                result.getTotalPages()
        );

        return result;
    }


    /**
     * Updates the airline owned by the authenticated user.
     *
     * Related cache regions are evicted because airline identity,
     * alliance membership, status-independent projections, or dropdown
     * information may have changed.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = "airlinesByOwner",
                    key = "#ownerId"
            ),
            @CacheEvict(
                    cacheNames = "airlines",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesByIata",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesByAlliance",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesDropdown",
                    allEntries = true
            )
    })
    public AirlineResponse updateAirline(
            AirlineRequest request,
            Long ownerId
    ) {

        log.info(
                "Updating airline for ownerId={}",
                ownerId
        );

        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> {

                    log.warn(
                            "Airline update failed: no airline found for ownerId={}",
                            ownerId
                    );

                    return new AirlineNotFoundException(ownerId);
                });


        Long airlineId = airline.getId();
        String oldIataCode = airline.getIataCode();

        // Apply incoming values to the managed airline entity.
        AirlineMapper.updateEntity(airline, request);

        Airline saved =
                airlineRepository.save(airline);

        log.info(
                "Airline updated successfully airlineId={} ownerId={} oldIataCode={} newIataCode={}",
                airlineId,
                ownerId,
                oldIataCode,
                saved.getIataCode()
        );

        return AirlineMapper.toResponse(saved);
    }


    /**
     * Deletes an airline after verifying that it belongs to the
     * authenticated owner.
     *
     * All dependent airline cache views are invalidated after successful
     * method completion.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = "airlines",
                    key = "#id"
            ),
            @CacheEvict(
                    cacheNames = "airlinesByOwner",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesByIata",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesByAlliance",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesDropdown",
                    allEntries = true
            )
    })
    public void deleteAirline(
            Long id,
            Long ownerId
    ) {

        log.info(
                "Deleting airline airlineId={} ownerId={}",
                id,
                ownerId
        );

        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> {

                    log.warn(
                            "Airline deletion failed: airlineId={} not found",
                            id
                    );

                    return new AirlineNotFoundException(id);
                });

        /*
         * Prevent an authenticated airline owner from deleting an airline
         * that belongs to another owner.
         */
        if (!airline.getOwnerId().equals(ownerId)) {

            log.warn(
                    "Airline deletion rejected: ownership mismatch airlineId={} requestedOwnerId={} actualOwnerId={}",
                    id,
                    ownerId,
                    airline.getOwnerId()
            );

            throw new AirlineOwnershipMismatchException();
        }

        airlineRepository.delete(airline);

        log.info(
                "Airline deleted successfully airlineId={} ownerId={} iataCode={}",
                id,
                ownerId,
                airline.getIataCode()
        );
    }


    // ==================== Business Operations ====================

    /**
     * Changes the operational status of an airline.
     *
     * This operation is intended for administrative lifecycle actions
     * such as approval, suspension, and banning.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = "airlines",
                    key = "#airlineId"
            ),
            @CacheEvict(
                    cacheNames = "airlinesByOwner",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesByAlliance",
                    allEntries = true
            ),
            @CacheEvict(
                    cacheNames = "airlinesDropdown",
                    allEntries = true
            )
    })
    public AirlineResponse changeStatusByAdmin(
            Long airlineId,
            AirlineStatus status
    ) {

        log.info(
                "Changing airline status airlineId={} requestedStatus={}",
                airlineId,
                status
        );

        Airline airline = airlineRepository.findById(airlineId)
                .orElseThrow(() -> {

                    log.warn(
                            "Airline status change failed: airlineId={} not found",
                            airlineId
                    );

                    return new AirlineNotFoundException(airlineId);
                });

        AirlineStatus previousStatus =
                airline.getStatus();

        airline.setStatus(status);

        Airline saved =
                airlineRepository.save(airline);

        log.info(
                "Airline status changed successfully airlineId={} previousStatus={} newStatus={}",
                airlineId,
                previousStatus,
                status
        );

        return AirlineMapper.toResponse(saved);
    }


    // ==================== Search / Filter Operations ====================

    /**
     * Returns active airlines as lightweight dropdown projections.
     *
     * Only ACTIVE airlines are exposed to booking, flight, and other
     * selection interfaces.
     */
    @Override
    @Cacheable(cacheNames = "airlinesDropdown")
    public List<AirlineDropdownItem> getAirlinesForDropdown() {

        log.debug(
                "Fetching active airlines for dropdown"
        );

        List<AirlineDropdownItem> result =
                airlineRepository.findByStatus(AirlineStatus.ACTIVE)
                        .stream()
                        .map(airline ->
                                AirlineDropdownItem.builder()
                                        .id(airline.getId())
                                        .name(airline.getName())
                                        .iataCode(airline.getIataCode())
                                        .icaoCode(airline.getIcaoCode())
                                        .logoUrl(airline.getLogoUrl())
                                        .country(airline.getCountry())
                                        .build()
                        )
                        .toList();

        log.debug(
                "Active airline dropdown data retrieved count={}",
                result.size()
        );

        return result;
    }
}