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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightCabinAncillaryServiceImpl implements FlightCabinAncillaryService {

    private final FlightCabinAncillaryRepository repository;
    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    private FlightCabinAncillaryResponse mapWithCoverages(
            FlightCabinAncillary entity) {
        List<InsuranceCoverage> coverages = insuranceCoverageRepository
                .findByAncillary(entity.getAncillary());
        List<InsuranceCoverageResponse> coverageResponses = coverages.stream()
                .map(InsuranceCoverageMapper::toResponse)
                .toList();
        return FlightCabinAncillaryMapper.toResponse(entity, coverageResponses);
    }

    @Override
    public FlightCabinAncillaryResponse create(FlightCabinAncillaryRequest req)
            throws ResourceNotFoundException {
        Ancillary ancillary = ancillaryRepository.findById(req.getAncillaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary not found"));

        FlightCabinAncillary entity = FlightCabinAncillary.builder()
                .flightId(req.getFlightId())
                .cabinClassId(req.getCabinClassId())
                .ancillary(ancillary)
                .available(req.getAvailable())
                .maxQuantity(req.getMaxQuantity())
                .price(req.getPrice())
                .currency(req.getCurrency())
                .includedInFare(req.getIncludedInFare())
                .build();

        return mapWithCoverages(repository.save(entity));
    }

    @Override
    public List<FlightCabinAncillaryResponse> bulkCreate(List<FlightCabinAncillaryRequest> requests)
            throws ResourceNotFoundException {
        return requests.stream()
                .map(req -> {
                    try {
                        return create(req);
                    } catch (ResourceNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public FlightCabinAncillaryResponse getById(Long id) throws ResourceNotFoundException {
        FlightCabinAncillary entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightCabinAncillary not found"));
        return mapWithCoverages(entity);
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByFlightAndCabinClass(Long flightId, Long cabinClassId) {
        return repository.findByFlightIdAndCabinClassId(flightId, cabinClassId).stream()
                .map(this::mapWithCoverages)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByIds(List<Long> ids) {
        List<FlightCabinAncillary> ancillaries = repository.findAllById(ids);
        return ancillaries.stream().map(
                this::mapWithCoverages
        ).collect(Collectors.toList());
    }


    @Override
    public FlightCabinAncillaryResponse getByFlightIdAndCabinClassAndType(
            Long flightId, Long cabinClassId, AncillaryType type) throws ResourceNotFoundException {
        FlightCabinAncillary entity = repository
                .findByFlightIdAndCabinClassIdAndAncillary_Type(flightId, cabinClassId, type)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FlightCabinAncillary not found for type: " + type));
        return mapWithCoverages(entity);
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassAndType(Long flightId, Long cabinClassId, AncillaryType type) throws ResourceNotFoundException {
        List<FlightCabinAncillary> ancillaries=
                repository.findAllByFlightIdAndCabinClassIdAndAncillary_Type(
                flightId, cabinClassId, type
        );
        return ancillaries.stream().map(
                this::mapWithCoverages
        ).collect(Collectors.toList());
    }


    @Override
    public FlightCabinAncillaryResponse update(Long id, FlightCabinAncillaryRequest req)
            throws ResourceNotFoundException {
        FlightCabinAncillary entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightCabinAncillary not found"));

        entity.setAvailable(req.getAvailable());
        entity.setMaxQuantity(req.getMaxQuantity());
        entity.setPrice(req.getPrice());
        entity.setCurrency(req.getCurrency());
        entity.setIncludedInFare(req.getIncludedInFare());

        return mapWithCoverages(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Double calculateAncillaryPrice(List<Long> ancillaryIds) {
        List<FlightCabinAncillary> ancillaries = repository.findAllById(ancillaryIds);

        double totalPrice = 0;
        for (FlightCabinAncillary ancillary : ancillaries) {
            totalPrice += ancillary.getPrice();
        }
        return totalPrice;
    }
}
