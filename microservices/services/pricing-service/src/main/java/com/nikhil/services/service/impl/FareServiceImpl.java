package com.nikhil.services.service.impl;


import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.services.mapper.FareMapper;
import com.nikhil.services.model.Fare;
import com.nikhil.services.repository.FareRepository;
import com.nikhil.services.service.FareService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

/**
 * Fare CRUD and search logic for pricing-service.
 * Gateway: /api/fares/** (JWT). Feign callers: flight-ops-service PricingClient,
 * booking-service PricingClient during checkout. No outbound Feign; stores flightId
 * and cabinClassId as cross-service references. Redis caches individual fare lookups.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FareServiceImpl implements FareService {

    private final FareRepository fareRepository;

    @Override
    public FareResponse createFare(FareRequest request) {
        if (fareRepository.existsByFlightIdAndCabinClassIdAndName(
                request.getFlightId(), request.getCabinClassId(), request.getName())) {
            throw new IllegalArgumentException(
                    "Fare '" + request.getName() + "' already exists for this flight and cabin class");
        }
        Fare fare = FareMapper.toEntity(request);
        Fare saved = fareRepository.save(fare);
        return FareMapper.toResponse(saved);
    }

    @Override
    public List<FareResponse> createFares(List<FareRequest> requests) {
        // Single DB call: fetch composite keys for all relevant flightIds
        Set<Long> flightIds = requests.stream()
                .map(FareRequest::getFlightId)
                .collect(Collectors.toSet());
        Set<String> existingKeys = fareRepository.findExistingFareKeys(flightIds);

        List<Fare> toSave = requests.stream()
                .filter(req -> !existingKeys.contains(
                        req.getFlightId() + ":" + req.getCabinClassId() + ":" + req.getName()))
                .map(FareMapper::toEntity)
                .collect(Collectors.toList());

        return fareRepository.saveAll(toSave).stream()
                .map(FareMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "fares", key = "#id")
    public FareResponse getFareById(Long id) {
        Fare fare = fareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fare not found with id: " + id));
        return FareMapper.toResponse(fare);
    }

//    @Override
//    @Transactional(readOnly = true)
//    public FareResponse getFareByIdWithDetails(Long id) {
//        Fare fare = fareRepository.findByIdWithDetails(id)
//                .orElseThrow(() -> new EntityNotFoundException("Fare not found with id: " + id));
//        return FareMapper.toResponse(fare);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    @Cacheable(cacheNames = "faresByFlight", key = "#flightId")
//    public List<FareResponse> getFaresByFlightId(Long flightId) {
//        return fareRepository.findByFlightId(flightId).stream()
//                .map(FareMapper::toResponse)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<FareResponse> getFaresByFlightIdWithDetails(Long flightId) {
//        return fareRepository.findByFlightIdWithDetails(flightId).stream()
//                .map(FareMapper::toResponse)
//                .collect(Collectors.toList());
//    }

    @Override
    @Transactional(readOnly = true)
    public List<FareResponse> getFaresByFlightIdAndCabinClassId(Long flightId, Long cabinClassId) {
        return fareRepository.findByFlightIdAndCabinClassId(flightId, cabinClassId).stream()
                .map(FareMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "fares", key = "#id"),
            @CacheEvict(cacheNames = "faresByFlight", allEntries = true)
    })
    public FareResponse updateFare(Long id, FareRequest request) {
        Fare existing = fareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fare not found with id: " + id));

        if (fareRepository.existsByFlightIdAndCabinClassIdAndNameAndIdNot(
                request.getFlightId(), request.getCabinClassId(), request.getName(), id)) {
            throw new IllegalArgumentException(
                    "Fare '" + request.getName() + "' already exists for this flight and cabin class");
        }

        FareMapper.updateEntity(request, existing);
        Fare saved = fareRepository.save(existing);
        return FareMapper.toResponse(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "fares", key = "#id"),
            @CacheEvict(cacheNames = "faresByFlight", allEntries = true)
    })
    public void deleteFare(Long id) {
        Fare fare = fareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fare not found with id: " + id));
        fareRepository.delete(fare);
    }

    @Override
    public List<Fare> getFares() {
        return fareRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FareResponse> getFaresByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return fareRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Fare::getId, FareMapper::toResponse));
    }

    /**
     * Batch lowest fare per flight; invoked by flight-ops search via POST /api/fares/search.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId) {
        if (flightIds == null || flightIds.isEmpty()) return Map.of();

        List<Fare> fares = fareRepository.findByFlightIdInAndCabinClassId(flightIds, cabinClassId);

        System.out.println("fares: -----------: " + fares.size());

        // Group by flightId and keep only the cheapest fare per flight
        Map<Long,FareResponse> result= fares.stream()
                .collect(Collectors.toMap(
                        Fare::getFlightId,
                        fare -> fare,
                        // merge: keep the fare with the lower total price
                        (existing, candidate) ->
                                candidate.getTotalPrice() < existing.getTotalPrice()
                                        ? candidate : existing
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> FareMapper.toResponse(e.getValue())
                ));
        System.out.println("result: -----------: lowest fare" + result);
        return result;
    }

    /**
     * Single lowest fare; used when each instance may map to a different cabinClassId.
     */
    @Override
    public FareResponse getLowestFareForFlightAndCabin(Long flightId, Long cabinClassId) {

        List<Fare> fares = fareRepository.findByFlightIdAndCabinClassId(
                flightId,
                cabinClassId
        );

        Fare lowestFare = fares.stream()
                .min(Comparator.comparingDouble(Fare::getTotalPrice))
                .orElse(null);

        return FareMapper.toResponse(lowestFare);
    }
}
