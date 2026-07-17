package com.nikhil.services.event;

import com.nikhil.common_lib.enums.SeatAvailabilityStatus;
import com.nikhil.common_lib.event.FlightInstanceCreatedEvent;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.FlightInstanceCabin;
import com.nikhil.services.model.Seat;
import com.nikhil.services.model.SeatInstance;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.repository.FlightInstanceCabinRepository;
import com.nikhil.services.repository.SeatInstanceRepository;
import com.nikhil.services.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightInstanceEventConsumer {

    private final CabinClassRepository cabinClassRepository;
    private final SeatRepository seatRepository;
    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final SeatInstanceRepository seatInstanceRepository;

    /*
     * Creates seat inventory for a newly generated flight instance.
     *
     * Event Flow:
     * Flight Ops Service → flight-instance-created
     *      ↓
     * Seat Service creates FlightInstanceCabin and SeatInstance rows.
     */
    @KafkaListener(topics = "flight-instance-created", groupId = "seat-service-group")
    @Transactional
    public void handleFlightInstanceCreated(FlightInstanceCreatedEvent event) {
        log.info("Received FlightInstanceCreatedEvent: flightInstanceId={}, aircraftId={}",
                event.getFlightInstanceId(), event.getAircraftId());

        List<CabinClass> cabinClasses = cabinClassRepository.findByAircraftId(event.getAircraftId());
        int totalSeatInstances = 0;

        for (CabinClass cabinClass : cabinClasses) {
            List<Seat> seats = cabinClass.getSeatMap() != null
                    ? seatRepository.findBySeatMapId(cabinClass.getSeatMap().getId())
                    : List.of();

            FlightInstanceCabin fic = FlightInstanceCabin.builder()
                    .flightInstanceId(event.getFlightInstanceId())
                    .cabinClass(cabinClass)
                    .totalSeats(seats.size())
                    .bookedSeats(0)
                    .build();

            FlightInstanceCabin savedFic = flightInstanceCabinRepository.save(fic);

            // Generate SeatInstances for each seat in this cabin
            List<SeatInstance> seatInstances = seats.stream()
                    .map(seat -> SeatInstance.builder()
                            .flightId(event.getFlightId())
                            .flightInstanceId(event.getFlightInstanceId())
                            .flightInstanceCabin(savedFic)
                            .seat(seat)
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .isAvailable(true)
                            .isBooked(false)
                            .premiumSurcharge(seat.getPremiumSurcharge())
                            .build())
                    .toList();

            seatInstanceRepository.saveAll(seatInstances);
            totalSeatInstances += seatInstances.size();
        }

        log.info("Created {} FlightInstanceCabin and {} SeatInstance records for flightInstanceId={}",
                cabinClasses.size(), totalSeatInstances, event.getFlightInstanceId());
    }
}
