package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AircraftRequest;
import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.services.mapper.AircraftMapper;
import com.nikhil.services.model.Aircraft;
import com.nikhil.services.model.Airline;
import com.nikhil.services.repository.AircraftRepository;
import com.nikhil.services.repository.AirlineRepository;
import com.nikhil.services.service.AircraftService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AircraftServiceImpl implements AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AirlineRepository airlineRepository;

    @Override
    public AircraftResponse createAircraft(AircraftRequest request, Long ownerId)
            throws ResourceNotFoundException {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));

        Aircraft aircraft = AircraftMapper.toEntity(request, airline);

        if (aircraftRepository.existsByCode(aircraft.getCode())) {
            throw new IllegalArgumentException("Aircraft with code " + aircraft.getCode() + " already exists");
        }

        validateAircraftData(aircraft);
        return AircraftMapper.toResponse(aircraftRepository.save(aircraft));
    }

    @Override
    @Cacheable(cacheNames = "aircrafts", key = "#id")
    public AircraftResponse getAircraftById(Long id) throws ResourceNotFoundException {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with id: " + id));
        return AircraftMapper.toResponse(aircraft);
    }

    @Override
    public List<AircraftResponse> listAllAircraftsByOwner(Long ownerId) {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));
        return aircraftRepository.findByAirline(airline)
                .stream()
                .map(AircraftMapper::toResponse)
                .toList();
    }

    @Override
    @CacheEvict(cacheNames = "aircrafts", key = "#id")
    public AircraftResponse updateAircraft(Long id, AircraftRequest request, Long ownerId)
            throws ResourceNotFoundException {
        Airline airline = airlineRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Airline not found for owner: " + ownerId));

        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with id: " + id));

        String oldCode = aircraft.getCode();
        AircraftMapper.updateEntity(aircraft, request, airline);

        if (!oldCode.equals(request.getCode()) && aircraftRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Aircraft with code " + request.getCode() + " already exists");
        }

        validateAircraftData(aircraft);
        return AircraftMapper.toResponse(aircraftRepository.save(aircraft));
    }

    @Override
    @CacheEvict(cacheNames = "aircrafts", key = "#id")
    public void deleteAircraft(Long id) throws ResourceNotFoundException {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with id: " + id));
        aircraftRepository.delete(aircraft);
    }

    private void validateAircraftData(Aircraft aircraft) {
        if (aircraft.getSeatingCapacity() != null && aircraft.getSeatingCapacity() <= 0) {
            throw new IllegalArgumentException("Seating capacity must be positive");
        }

        int totalSpecifiedSeats = (aircraft.getEconomySeats() != null ? aircraft.getEconomySeats() : 0) +
                (aircraft.getPremiumEconomySeats() != null ? aircraft.getPremiumEconomySeats() : 0) +
                (aircraft.getBusinessSeats() != null ? aircraft.getBusinessSeats() : 0) +
                (aircraft.getFirstClassSeats() != null ? aircraft.getFirstClassSeats() : 0);

        if (totalSpecifiedSeats > aircraft.getSeatingCapacity()) {
            throw new IllegalArgumentException("Total specified seats exceed aircraft seating capacity");
        }

        if (aircraft.getYearOfManufacture() != null &&
                (aircraft.getYearOfManufacture() < 1900
                        || aircraft.getYearOfManufacture() > LocalDate.now().getYear())) {
            throw new IllegalArgumentException("Invalid year of manufacture");
        }
    }
}
