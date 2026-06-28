package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.CabinClassRequest;
import com.nikhil.common_lib.payload.response.CabinClassResponse;
import com.nikhil.services.mapper.CabinClassMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.service.CabinClassService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CabinClassServiceImpl implements CabinClassService {

    private final CabinClassRepository cabinClassRepository;

    @Override
    public CabinClassResponse createCabinClass(CabinClassRequest request) {
        if (cabinClassRepository.existsByCodeAndAircraftId(
                request.getCode().toUpperCase(),
                        request.getAircraftId())) {
            throw new IllegalArgumentException(
                    "Cabin class with code '" + request.getCode() + "' already exists for this aircraft");
        }
        CabinClass cabinClass = CabinClassMapper.toEntity(request);
        cabinClass.setAircraftId(request.getAircraftId());
        CabinClass saved = cabinClassRepository.save(cabinClass);
        return CabinClassMapper.toResponse(saved, null);
    }

    @Override
    public List<CabinClassResponse> createCabinClasses(List<CabinClassRequest> requests) {
        List<CabinClass> toSave = requests.stream()
                .filter(req -> !cabinClassRepository.existsByCodeAndAircraftId(
                        req.getCode().toUpperCase(), req.getAircraftId()))
                .map(req -> {
                    CabinClass cc = CabinClassMapper.toEntity(req);
                    cc.setAircraftId(req.getAircraftId());
                    return cc;
                })
                .collect(Collectors.toList());
        return cabinClassRepository.saveAll(toSave).stream()
                .map(cc -> CabinClassMapper.toResponse(cc, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CabinClassResponse getCabinClassById(Long id) {
        CabinClass cabinClass = cabinClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cabin class not found with id: " + id));
        return CabinClassMapper.toResponse(cabinClass, cabinClass.getSeatMap());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CabinClassResponse> getCabinClassesByAircraftId(Long aircraftId) {
        return cabinClassRepository.findByAircraftId(aircraftId).stream()
                .map(cc -> CabinClassMapper.toResponse(cc, cc.getSeatMap()))
                .collect(Collectors.toList());
    }

    @Override
    public CabinClassResponse getByAircraftIdAndName(Long aircraftId, CabinClassType name) {
        CabinClass cabinClass= cabinClassRepository.findByAircraftIdAndName(aircraftId,name);
        return CabinClassMapper.toResponse(cabinClass,null);
    }

    @Override
    public CabinClassResponse updateCabinClass(Long id, CabinClassRequest request) {
        CabinClass existing = cabinClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cabin class not found with id: " + id));

        if (cabinClassRepository.existsByCodeAndAircraftIdAndIdNot(
                request.getCode().toUpperCase(), existing.getAircraftId(), id)) {
            throw new IllegalArgumentException(
                    "Cabin class with code '" + request.getCode() + "' already exists for this aircraft");
        }

        CabinClassMapper.updateEntity(request, existing);
        CabinClass saved = cabinClassRepository.save(existing);
        return CabinClassMapper.toResponse(saved, saved.getSeatMap());
    }

    @Override
    public void deleteCabinClass(Long id) {
        CabinClass cabinClass = cabinClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cabin class not found with id: " + id));
        cabinClassRepository.delete(cabinClass);
    }
}
