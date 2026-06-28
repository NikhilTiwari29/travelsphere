package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.enums.SeatType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightInstanceCabinRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceCabinResponse;
import com.nikhil.services.mapper.FlightInstanceCabinMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.FlightInstanceCabin;
import com.nikhil.services.model.SeatInstance;
import com.nikhil.services.model.SeatMap;
import com.nikhil.services.repository.*;
import com.nikhil.services.service.FlightInstanceCabinService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FlightInstanceCabinServiceImpl implements FlightInstanceCabinService {

    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final CabinClassRepository cabinClassRepository;
    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatInstanceRepository seatInstanceRepository;

    @Override
    public FlightInstanceCabinResponse createFlightInstanceCabin(FlightInstanceCabinRequest request)
            throws ResourceNotFoundException {
        CabinClass cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cabin class not found with id: " + request.getCabinClassId())
                );

        // Set total seats from the seat map
        SeatMap seatMap = seatMapRepository.findByCabinClassId(cabinClass.getId());

        if (seatMap == null) {
            throw new EntityNotFoundException(
                    "seat map not found with cabin class id: " + cabinClass.getId()
            );
        }

        if (seatMap.getSeats() == null || seatMap.getSeats().isEmpty()) {
            throw new ResourceNotFoundException(
                    "No seats found in the seat map for cabin class id " + cabinClass.getId()
            );
        }
        int totalSeats = seatMap.getSeats().size();

        FlightInstanceCabin fic = FlightInstanceCabin.builder()
                .flightInstanceId(request.getFlightInstanceId())
                .cabinClass(cabinClass)
                .totalSeats(totalSeats)
                .bookedSeats(0)
                .build();

        FlightInstanceCabin savedFlightInstanceCabin = flightInstanceCabinRepository.save(fic);

//      generate SeatInstances
        List<SeatInstance> seatInstances = seatMap.getSeats().stream()
                .map(seat -> {
                    Double premiumSurcharge = getPremiumSurcharge(
                            seat.getSeatType(),
                            1000.0,
                            500.0
                    );
                    return SeatInstance.builder()
                            .flightId(request.getFlightId())
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .flightInstanceId(request.getFlightInstanceId())
                            .flightInstanceCabin(savedFlightInstanceCabin)
                            .seat(seat)
                            .isAvailable(true)
                            .isBooked(false)
                            .premiumSurcharge(premiumSurcharge)
                            .build();
                })
                .toList();
        seatInstanceRepository.saveAll(seatInstances);
        savedFlightInstanceCabin.setSeats(seatInstances);

        return FlightInstanceCabinMapper.toResponse(savedFlightInstanceCabin);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightInstanceCabinResponse getFlightInstanceCabinById(Long id) {
        FlightInstanceCabin fic = flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance cabin not found with id: " + id));
        return FlightInstanceCabinMapper.toResponse(fic);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightInstanceCabinResponse> getByFlightInstanceId(Long flightInstanceId, Pageable pageable) {
        return flightInstanceCabinRepository.findByFlightInstanceId(flightInstanceId, pageable)
                .map(FlightInstanceCabinMapper::toResponse);
    }

    @Override
    public FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(Long flightInstanceId, Long cabinClassId) {
       FlightInstanceCabin cabin= flightInstanceCabinRepository.findByFlightInstanceIdAndCabinClassId(
                flightInstanceId,
                cabinClassId
       );
       return FlightInstanceCabinMapper.toResponse(cabin);
    }

    @Override
    public FlightInstanceCabinResponse updateFlightInstanceCabin(Long id, FlightInstanceCabinRequest request) {
        FlightInstanceCabin existing = flightInstanceCabinRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance cabin not found with id: " + id));

        if (request.getCabinClassId() != null) {
            CabinClass cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cabin class not found with id: " + request.getCabinClassId()));
            existing.setCabinClass(cabinClass);
        }

        FlightInstanceCabin saved = flightInstanceCabinRepository.save(existing);
        return FlightInstanceCabinMapper.toResponse(saved);
    }

    @Override
    public void deleteFlightInstanceCabin(Long id) {
        FlightInstanceCabin fic = flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Flight instance cabin not found with id: " + id));
        flightInstanceCabinRepository.delete(fic);
    }
    private Double getPremiumSurcharge(SeatType seatType,
                                       Double windowSurcharge,
                                       Double aisleSurcharge) {
        if (seatType == null) return 0.0;

        return switch (seatType) {
            case WINDOW -> windowSurcharge != null ? windowSurcharge : 0.0;
            case AISLE -> aisleSurcharge != null ? aisleSurcharge : 0.0;
            default -> 0.0;
        };
    }
}
