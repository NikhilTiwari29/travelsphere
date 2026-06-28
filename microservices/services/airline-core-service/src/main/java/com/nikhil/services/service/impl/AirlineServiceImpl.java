package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.AirlineStatus;
import com.nikhil.common_lib.payload.request.AirlineRequest;
import com.nikhil.common_lib.payload.response.AirlineDropdownItem;
import com.nikhil.common_lib.payload.response.AirlineResponse;
import com.nikhil.services.mapper.AirlineMapper;
import com.nikhil.services.model.Airline;
import com.nikhil.services.repository.AirlineRepository;
import com.nikhil.services.service.AirlineService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;

    // ---------- CRUD ----------

    @Override
    public AirlineResponse createAirline(AirlineRequest request, Long ownerId) {
        Airline airline = AirlineMapper.toEntity(request, ownerId);
        Airline saved = airlineRepository.save(airline);
        return AirlineMapper.toResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "airlinesByOwner", key = "#ownerId")
    public AirlineResponse getAirlineByOwner(Long ownerId) {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));
        return AirlineMapper.toResponse(airline);
    }

    @Override
    @Cacheable(cacheNames = "airlines", key = "#id")
    public AirlineResponse getAirlineById(Long id) {
        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found"));
        return AirlineMapper.toResponse(airline);
    }

    @Override
    public Page<AirlineResponse> getAllAirlines(Pageable pageable) {
        return airlineRepository
                .findAll(pageable).map(AirlineMapper::toResponse);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "airlinesByOwner", key = "#ownerId"),
            @CacheEvict(cacheNames = "airlines", allEntries = true),
            @CacheEvict(cacheNames = "airlinesByIata", allEntries = true),
            @CacheEvict(cacheNames = "airlinesByAlliance", allEntries = true)
    })
    public AirlineResponse updateAirline(AirlineRequest request, Long ownerId) {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));

        AirlineMapper.updateEntity(airline, request);
        return AirlineMapper.toResponse(airlineRepository.save(airline));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "airlines", key = "#id"),
            @CacheEvict(cacheNames = "airlinesByOwner", allEntries = true),
            @CacheEvict(cacheNames = "airlinesByIata", allEntries = true),
            @CacheEvict(cacheNames = "airlinesByAlliance", allEntries = true)
    })
    public void deleteAirline(Long id, Long ownerId) {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));
        airlineRepository.delete(airline);
    }

    // ---------- Business Operations ----------



    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "airlines", key = "#airlineId"),
            @CacheEvict(cacheNames = "airlinesByAlliance", allEntries = true)
    })
    public AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status) {
        Airline airline = airlineRepository.findById(airlineId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found with ID: " + airlineId));
        airline.setStatus(status);
        return AirlineMapper.toResponse(airlineRepository.save(airline));
    }


    // ---------- Search / Filters ----------

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "airlinesDropdown")
    public List<AirlineDropdownItem> getAirlinesForDropdown() {
        return airlineRepository.findByStatus(AirlineStatus.ACTIVE).stream()
                .map(a -> AirlineDropdownItem.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .iataCode(a.getIataCode())
                        .icaoCode(a.getIcaoCode())
                        .logoUrl(a.getLogoUrl())
                        .country(a.getCountry())
                        .build())
                .toList();
    }


}
