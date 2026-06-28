package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.SeatType;
import com.nikhil.common_lib.payload.request.SeatRequest;
import com.nikhil.common_lib.payload.response.SeatResponse;
import com.nikhil.services.mapper.SeatMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.Seat;
import com.nikhil.services.model.SeatMap;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.repository.SeatMapRepository;
import com.nikhil.services.repository.SeatRepository;
import com.nikhil.services.service.SeatService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final CabinClassRepository cabinClassRepository;


    public void generateSeats(Long seatMapId
    ) throws Exception {

        boolean exists = seatRepository.existsBySeatMapId(seatMapId);

        if (exists) {
            throw new Exception("Seats already created for seat map id " + seatMapId);
        }


        SeatMap seatMap=seatMapRepository.findById(seatMapId).orElseThrow(
                ()->  new ResourceNotFoundException("seat map not found")
        );

        int leftSeatsPerRow=seatMap.getLeftSeatsPerRow();
        int rightSeatsPerRow=seatMap.getRightSeatsPerRow();
        int rows=seatMap.getTotalRows();
        int seatsPerRow = leftSeatsPerRow + rightSeatsPerRow;


        List<Seat> seats = new ArrayList<>();


        for (int row = 1; row <= rows; row++) {
            for (int col = 0; col < seatsPerRow; col++) {
                String seatNum = row + getSeatLetter(col);
                SeatType type = getSeatType(col, leftSeatsPerRow, rightSeatsPerRow);

                seats.add(Seat.builder()
                        .seatNumber(seatNum)
                        .seatRow(row)
                        .columnLetter(getSeatLetter(col).charAt(0))
                        .seatType(type)
                        .seatMap(seatMap)
                        .build());
            }
        }

        seatRepository.saveAll(seats);

    }

    // 🚀 seat letter handling
    private String getSeatLetter(int col) {
        StringBuilder sb = new StringBuilder();
        while (col >= 0) {
            sb.insert(0, (char) ('A' + (col % 26)));
            col = col / 26 - 1;
        }
        return sb.toString();
    }

    private SeatType getSeatType(int seatIndex, int leftBlockSeats, int rightBlockSeats) {
        int totalSeats = leftBlockSeats + rightBlockSeats;

        // Windows
        if (seatIndex == 0 || seatIndex == totalSeats - 1) return SeatType.WINDOW;

        // Left aisle
        if (seatIndex == leftBlockSeats - 1) return SeatType.AISLE;

        // Right aisle
        if (seatIndex == leftBlockSeats) return SeatType.AISLE;

        // Everything else
        return SeatType.MIDDLE;
    }

    @Override
    @Transactional(readOnly = true)
    public SeatResponse getSeatById(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Seat not found with id: " + id));
        return SeatMapper.toResponse(seat);
    }

    @Override
    public List<SeatResponse> getAll() {
        return seatRepository.findAll().stream()
                .map(SeatMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SeatResponse updateSeat(Long id, SeatRequest request) {
        Seat existing = seatRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Seat not found with id: " + id));

        SeatMap seatMap = seatMapRepository.findById(request.getSeatMapId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Seat map not found with id: " + request.getSeatMapId()));

        CabinClass cabinClass = null;
        if (request.getCabinClassId() != null) {
            cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cabin class not found with id: " + request.getCabinClassId()));
        }

        SeatMapper.updateEntity(request, existing, seatMap, cabinClass);
        Seat saved = seatRepository.save(existing);
        return SeatMapper.toResponse(saved);
    }


}
