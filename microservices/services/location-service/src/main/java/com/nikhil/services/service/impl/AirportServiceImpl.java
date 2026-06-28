package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.CityException;
import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.payload.response.AirportResponse;
import com.nikhil.services.mapper.AirportMapper;
import com.nikhil.services.model.Airport;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.AirportRepository;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.AirportService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;

    @Override
    @Transactional
    public AirportResponse createAirport(AirportRequest request) throws AirportException, CityException {
        if (airportRepository.findByIataCode(request.getIataCode()).isPresent()) {
            throw new AirportException("Airport with IATA code " + request.getIataCode() + " already exists.");
        }

        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new CityException("City not found with id: " + request.getCityId()));

        Airport airport = AirportMapper.toEntity(request);
        airport.setCity(city);

        Airport savedAirport = airportRepository.save(airport);
        return AirportMapper.toResponse(savedAirport);
    }

    @Override
    @Transactional
    public List<AirportResponse> createBulkAirports(List<AirportRequest> requests)
            throws AirportException, CityException {
        List<AirportResponse> createdAirports = new ArrayList<>();
        List<String> skippedCodes = new ArrayList<>();

        for (AirportRequest request : requests) {
            if (airportRepository.findByIataCode(request.getIataCode()).isPresent()) {
                skippedCodes.add(request.getIataCode() + " (already exists)");
                continue;
            }

            Optional<City> cityOpt = cityRepository.findById(request.getCityId());
            if (cityOpt.isEmpty()) {
                skippedCodes.add(request.getIataCode() + " (city not found with id: " + request.getCityId() + ")");
                continue;
            }

            Airport airport = AirportMapper.toEntity(request);
            airport.setCity(cityOpt.get());

            Airport savedAirport = airportRepository.save(airport);
            createdAirports.add(AirportMapper.toResponse(savedAirport));
        }

        if (!skippedCodes.isEmpty()) {
            log.info("Bulk airport creation - skipped: {}", skippedCodes);
        }
        log.info("Bulk airport creation - created {} out of {} airports", createdAirports.size(), requests.size());

        return createdAirports;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "airports", key = "#id")
    public AirportResponse getAirportById(Long id) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Airport not found with id: " + id));
        return AirportMapper.toResponse(airport);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "allAirports")
    public List<AirportResponse> getAllAirports() {
        return airportRepository.findAll().stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "airports", key = "#id"),
            @CacheEvict(cacheNames = "allAirports", allEntries = true),
            @CacheEvict(cacheNames = "airportsByIata", allEntries = true),
            @CacheEvict(cacheNames = "airportsByCity", allEntries = true)
    })
    public AirportResponse updateAirport(Long id, AirportRequest request) throws AirportException, CityException {
        Airport existingAirport = airportRepository.findById(id)
                .orElseThrow(() -> new AirportException("Airport not found with id: " + id));

        if (request.getIataCode() != null
                && !existingAirport.getIataCode().equals(request.getIataCode())
                && airportRepository.findByIataCode(request.getIataCode()).isPresent()) {
            throw new AirportException("IATA code " + request.getIataCode() + " is already taken.");
        }

        if (request.getCityId() != null) {
            City newCity = cityRepository.findById(request.getCityId())
                    .orElseThrow(() -> new CityException("City not found with id: " + request.getCityId()));
            existingAirport.setCity(newCity);
        }

        AirportMapper.updateEntity(request, existingAirport);

        Airport updatedAirport = airportRepository.save(existingAirport);
        return AirportMapper.toResponse(updatedAirport);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "airports", key = "#id"),
            @CacheEvict(cacheNames = "allAirports", allEntries = true),
            @CacheEvict(cacheNames = "airportsByIata", allEntries = true),
            @CacheEvict(cacheNames = "airportsByCity", allEntries = true)
    })
    public void deleteAirport(Long id) throws AirportException {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new AirportException("Airport not found with id: " + id));
        airportRepository.delete(airport);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "airportsByCity", key = "#cityId")
    public List<AirportResponse> getAirportsByCityId(Long cityId) {
        return airportRepository.findByCityId(cityId).stream()
                .map(AirportMapper::toResponse)
                .collect(Collectors.toList());
    }
}
