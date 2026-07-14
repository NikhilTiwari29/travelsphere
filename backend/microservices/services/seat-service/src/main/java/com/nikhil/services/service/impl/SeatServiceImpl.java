package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.SeatType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for physical aircraft seat-template management.
 *
 * Seats are generated from the dimensions defined by a SeatMap. Each generated
 * Seat represents a permanent physical position in an aircraft cabin layout.
 *
 * Creation flow:
 *
 * SeatMapService
 *      → Save SeatMap
 *      → SeatService.generateSeats()
 *      → Generate physical Seat records
 *      → Persist Seats
 *
 * Domain hierarchy:
 *
 * Aircraft
 *      → CabinClass
 *          → SeatMap
 *              → Seat
 *                  → SeatInstance
 *
 * Seat represents a permanent physical position in the aircraft layout,
 * while SeatInstance represents the operational state of that seat for
 * a specific flight occurrence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final CabinClassRepository cabinClassRepository;


    // ==================== Seat Generation ====================

    /**
     * Generates physical Seat records from a SeatMap configuration.
     *
     * Flow:
     * Validate no Seats exist → Load SeatMap → Read layout dimensions
     * → Generate row/column positions → Determine SeatType → Save all Seats.
     *
     * This method is called from the transactional SeatMap creation flow.
     * Therefore, SeatMap persistence and Seat generation participate in the
     * same transaction and are committed or rolled back together.
     */
    @Override
    public void generateSeats(
            Long seatMapId
    ) throws Exception {

        log.info(
                "Seat generation started seatMapId={}",
                seatMapId
        );

        boolean exists =
                seatRepository.existsBySeatMapId(seatMapId);

        /*
         * Prevent duplicate physical Seat generation for the same SeatMap.
         */
        if (exists) {

            log.warn(
                    "Seat generation rejected: seats already exist seatMapId={}",
                    seatMapId
            );

            throw new Exception(
                    "Seats already created for seat map id " + seatMapId
            );
        }

        SeatMap seatMap =
                seatMapRepository.findById(seatMapId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat generation failed: seat map not found seatMapId={}",
                                    seatMapId
                            );

                            return new ResourceNotFoundException(
                                    "seat map not found"
                            );
                        });

        int leftSeatsPerRow =
                seatMap.getLeftSeatsPerRow();

        int rightSeatsPerRow =
                seatMap.getRightSeatsPerRow();

        int rows =
                seatMap.getTotalRows();

        int seatsPerRow =
                leftSeatsPerRow + rightSeatsPerRow;

        log.info(
                "Generating seat layout seatMapId={} rows={} leftSeatsPerRow={} rightSeatsPerRow={} seatsPerRow={} totalSeats={}",
                seatMapId,
                rows,
                leftSeatsPerRow,
                rightSeatsPerRow,
                seatsPerRow,
                rows * seatsPerRow
        );

        List<Seat> seats = new ArrayList<>();

        /*
         * Generate one Seat record for every physical position in the layout.
         *
         * Example for a 3 + 3 configuration:
         *
         * A B C | aisle | D E F
         *
         * The aisle separates the left and right seat blocks and does not
         * consume a seat column letter.
         */
        for (int row = 1; row <= rows; row++) {

            for (int col = 0; col < seatsPerRow; col++) {

                String seatNum =
                        row + getSeatLetter(col);

                SeatType type =
                        getSeatType(
                                col,
                                leftSeatsPerRow,
                                rightSeatsPerRow
                        );

                seats.add(
                        Seat.builder()
                                .seatNumber(seatNum)
                                .seatRow(row)
                                .columnLetter(
                                        getSeatLetter(col).charAt(0)
                                )
                                .seatType(type)
                                .seatMap(seatMap)
                                .build()
                );
            }
        }

        seatRepository.saveAll(seats);

        log.info(
                "Seat generation completed successfully seatMapId={} generatedSeatCount={}",
                seatMapId,
                seats.size()
        );
    }


    // ==================== Seat Layout Helpers ====================

    /**
     * Converts a zero-based column index into an alphabetical seat-column
     * identifier.
     *
     * Examples:
     * 0  → A
     * 1  → B
     * 25 → Z
     * 26 → AA
     */
    private String getSeatLetter(int col) {

        StringBuilder sb = new StringBuilder();

        while (col >= 0) {

            sb.insert(
                    0,
                    (char) ('A' + (col % 26))
            );

            col = col / 26 - 1;
        }

        return sb.toString();
    }


    /**
     * Determines the physical seat type from its position within the row.
     *
     * Rules:
     * - First and last seats are WINDOW seats.
     * - Seats immediately adjacent to the center aisle are AISLE seats.
     * - Remaining seats are MIDDLE seats.
     *
     * Example for a 3 + 3 layout:
     *
     * A(WINDOW) B(MIDDLE) C(AISLE)
     *                  aisle
     * D(AISLE) E(MIDDLE) F(WINDOW)
     */
    private SeatType getSeatType(
            int seatIndex,
            int leftBlockSeats,
            int rightBlockSeats
    ) {

        int totalSeats =
                leftBlockSeats + rightBlockSeats;

        // Outermost seats are adjacent to aircraft windows.
        if (seatIndex == 0
                || seatIndex == totalSeats - 1) {

            return SeatType.WINDOW;
        }

        // Last seat in the left block is adjacent to the aisle.
        if (seatIndex == leftBlockSeats - 1) {

            return SeatType.AISLE;
        }

        // First seat in the right block is adjacent to the aisle.
        if (seatIndex == leftBlockSeats) {

            return SeatType.AISLE;
        }

        // All remaining seats are middle seats.
        return SeatType.MIDDLE;
    }


    // ==================== Read Operations ====================

    /**
     * Returns a physical Seat template by its unique database ID.
     */
    @Override
    @Transactional(readOnly = true)
    public SeatResponse getSeatById(Long id) {

        log.debug(
                "Fetching seat seatId={}",
                id
        );

        Seat seat =
                seatRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat lookup failed: seat not found seatId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat not found with id: " + id
                            );
                        });

        log.debug(
                "Seat retrieved successfully seatId={} seatNumber={}",
                seat.getId(),
                seat.getSeatNumber()
        );

        return SeatMapper.toResponse(seat);
    }


    /**
     * Returns all physical Seat template records.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getAll() {

        log.debug(
                "Fetching all physical seat templates"
        );

        List<SeatResponse> responses =
                seatRepository.findAll()
                        .stream()
                        .map(SeatMapper::toResponse)
                        .collect(Collectors.toList());

        log.debug(
                "Physical seat templates retrieved count={}",
                responses.size()
        );

        return responses;
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing physical Seat template.
     *
     * Flow:
     * Load Seat → Load requested SeatMap → Optionally load CabinClass
     * → Apply request values → Save updated Seat.
     *
     * CabinClass is resolved only when cabinClassId is provided in the request.
     */
    @Override
    @Transactional
    public SeatResponse updateSeat(
            Long id,
            SeatRequest request
    ) {

        log.info(
                "Updating seat seatId={} seatMapId={} cabinClassId={}",
                id,
                request.getSeatMapId(),
                request.getCabinClassId()
        );

        Seat existing =
                seatRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat update failed: seat not found seatId={}",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat not found with id: " + id
                            );
                        });

        SeatMap seatMap =
                seatMapRepository.findById(request.getSeatMapId())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Seat update failed: seat map not found seatMapId={} seatId={}",
                                    request.getSeatMapId(),
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Seat map not found with id: "
                                            + request.getSeatMapId()
                            );
                        });

        CabinClass cabinClass = null;

        /*
         * CabinClass association is optional during update and is resolved
         * only when the request contains a cabinClassId.
         */
        if (request.getCabinClassId() != null) {

            cabinClass =
                    cabinClassRepository.findById(
                                    request.getCabinClassId()
                            )
                            .orElseThrow(() -> {

                                log.warn(
                                        "Seat update failed: cabin class not found cabinClassId={} seatId={}",
                                        request.getCabinClassId(),
                                        id
                                );

                                return new EntityNotFoundException(
                                        "Cabin class not found with id: "
                                                + request.getCabinClassId()
                                );
                            });
        }

        SeatMapper.updateEntity(
                request,
                existing,
                seatMap,
                cabinClass
        );

        Seat saved =
                seatRepository.save(existing);

        log.info(
                "Seat updated successfully seatId={} seatNumber={} seatMapId={}",
                saved.getId(),
                saved.getSeatNumber(),
                request.getSeatMapId()
        );

        return SeatMapper.toResponse(saved);
    }
}