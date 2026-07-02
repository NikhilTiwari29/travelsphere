package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.OperationNotPermittedException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.CityRequest;
import com.nikhil.common_lib.payload.response.CityResponse;
import com.nikhil.services.mapper.CityMapper;
import com.nikhil.services.model.City;
import com.nikhil.services.repository.CityRepository;
import com.nikhil.services.service.CityService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * City CRUD, search, and validation with Redis caching.
 * Gateway /api/cities/** → this service. Airports reference cities via local FK.
 * Data flow: CityRequest → CityRepository → CityResponse; no outbound Feign.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;

    // ---------- Core CRUD ----------

    @Override
    public CityResponse createCity(CityRequest request) throws OperationNotPermittedException {
        validateCityRequest(request);

        if (cityRepository.existsByCityCode(request.getCityCode())) {
            throw new OperationNotPermittedException("City with code " + request.getCityCode() + " already exists");
        }

        City city = CityMapper.toEntity(request);
        City savedCity = cityRepository.save(city);

        log.info("City created: {} ({})", savedCity.getName(), savedCity.getCityCode());
        return CityMapper.toResponse(savedCity);
    }

    @Override
    public List<CityResponse> createBulkCities(List<CityRequest> requests) throws OperationNotPermittedException {
        List<CityResponse> createdCities = new ArrayList<>();
        List<String> skippedCodes = new ArrayList<>();

        for (CityRequest request : requests) {
            try {
                validateCityRequest(request);
            } catch (IllegalArgumentException e) {
                skippedCodes.add(request.getCityCode() + " (invalid: " + e.getMessage() + ")");
                continue;
            }

            if (cityRepository.existsByCityCode(request.getCityCode())) {
                skippedCodes.add(request.getCityCode() + " (already exists)");
                continue;
            }

            City city = CityMapper.toEntity(request);
            City savedCity = cityRepository.save(city);
            createdCities.add(CityMapper.toResponse(savedCity));
        }

        if (!skippedCodes.isEmpty()) {
            log.info("Bulk city creation - skipped: {}", skippedCodes);
        }
        log.info("Bulk city creation - created {} out of {} cities", createdCities.size(), requests.size());

        return createdCities;
    }

    @Override
    @Cacheable(cacheNames = "cities", key = "#id")
    public CityResponse getCityById(Long id) throws ResourceNotFoundException {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));
        return CityMapper.toResponse(city);
    }


    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "cities", key = "#id"),
            @CacheEvict(cacheNames = "citiesByCode", allEntries = true)
    })
    public CityResponse updateCity(Long id, CityRequest request)
            throws ResourceNotFoundException, OperationNotPermittedException {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));

        validateCityRequest(request, id);

        if (cityRepository.existsByCityCodeAndIdNot(request.getCityCode(), id)) {
            throw new OperationNotPermittedException("City with code " + request.getCityCode() + " already exists");
        }

        City updatedCity = cityRepository.save(CityMapper.updateEntity(city, request));

        log.info("City updated: {} ({})", updatedCity.getName(), updatedCity.getCityCode());
        return CityMapper.toResponse(updatedCity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "cities", key = "#id"),
            @CacheEvict(cacheNames = "citiesByCode", allEntries = true)
    })
    public void deleteCity(Long id) throws ResourceNotFoundException {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));
        cityRepository.delete(city);
        log.info("City deleted: {} ({})", city.getName(), city.getCityCode());
    }

    @Override
    public Page<CityResponse> getAllCities(Pageable pageable) {
        return cityRepository.findAll(pageable).map(CityMapper::toResponse);
    }

    // ---------- Search & Query ----------

    @Override
    public Page<CityResponse> searchCities(String keyword, Pageable pageable) {
        return cityRepository.searchByKeyword(keyword, pageable)
                .map(CityMapper::toResponse);
    }

    @Override
    public Page<CityResponse> getCitiesByCountryCode(String countryCode, Pageable pageable) {
        return cityRepository.findByCountryCodeIgnoreCase(countryCode, pageable).map(CityMapper::toResponse);
    }



    // ---------- Validation ----------

    @Override
    public boolean cityExists(String cityCode) {
        return cityRepository.existsByCityCode(cityCode);
    }

    @Override
    public boolean validateCityCode(String cityCode) {
        return cityCode != null && cityCode.length() <= 10 && cityCode.matches("[A-Z0-9]{2,10}");
    }

    // ---------- Private Helpers ----------

    private void validateCityRequest(CityRequest request) {
        validateCityRequest(request, null);
    }

    private void validateCityRequest(CityRequest request, Long excludeId) {
        if (!validateCityCode(request.getCityCode())) {
            throw new IllegalArgumentException("Invalid city code format. Must be 2-10 alphanumeric characters.");
        }

        if (request.getCountryCode() == null || !request.getCountryCode().matches("[A-Z]{2,5}")) {
            throw new IllegalArgumentException("Country code must be 2-5 uppercase letters");
        }

        if (request.getTimeZoneOffset() != null && !request.getTimeZoneOffset().matches("[+-]\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("Time zone offset must be in format ±HH:MM");
        }
    }
}
