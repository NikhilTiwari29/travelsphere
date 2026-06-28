package com.nikhil.services.service.impl;

import com.nikhil.common_lib.payload.request.FareRulesRequest;
import com.nikhil.common_lib.payload.response.FareRulesResponse;
import com.nikhil.services.mapper.FareRulesMapper;
import com.nikhil.services.model.Fare;
import com.nikhil.services.model.FareRules;
import com.nikhil.services.repository.FareRepository;
import com.nikhil.services.repository.FareRulesRepository;
import com.nikhil.services.service.FareRulesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FareRulesServiceImpl implements FareRulesService {

    private final FareRulesRepository fareRulesRepository;
    private final FareRepository fareRepository;

    @Override
    public FareRulesResponse createFareRules(FareRulesRequest request) {
        Fare fare = fareRepository.findById(request.getFareId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fare not found with id: " + request.getFareId()));

        if (fareRulesRepository.existsByFareId(request.getFareId())) {
            throw new IllegalArgumentException(
                    "Fare rules already exist for fare id: " + request.getFareId());
        }

        FareRules fareRules = FareRulesMapper.toEntity(request, fare);
        FareRules saved = fareRulesRepository.save(fareRules);
        return FareRulesMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FareRulesResponse getFareRulesById(Long id) {
        FareRules fareRules = fareRulesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fare rules not found with id: " + id));
        return FareRulesMapper.toResponse(fareRules);
    }

    @Override
    @Transactional(readOnly = true)
    public FareRulesResponse getFareRulesByFareId(Long fareId) {
        FareRules fareRules = fareRulesRepository.findByFareId(fareId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fare rules not found for fare id: " + fareId));
        return FareRulesMapper.toResponse(fareRules);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId) {
        return fareRulesRepository.findByAirlineId(airlineId).stream()
                .map(FareRulesMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FareRulesResponse updateFareRules(Long id, FareRulesRequest request) {
        FareRules existing = fareRulesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fare rules not found with id: " + id));

        FareRulesMapper.updateEntity(request, existing);
        FareRules saved = fareRulesRepository.save(existing);
        return FareRulesMapper.toResponse(saved);
    }

    @Override
    public void deleteFareRules(Long id) {
        FareRules fareRules = fareRulesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fare rules not found with id: " + id));
        fareRulesRepository.delete(fareRules);
    }
}
