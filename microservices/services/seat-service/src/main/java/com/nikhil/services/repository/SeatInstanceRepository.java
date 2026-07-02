package com.nikhil.services.repository;

import com.nikhil.services.model.SeatInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/*
 * JPA repository for per-flight seat inventory (SeatInstance rows).
 *
 * SeatMap → Seat (template) → SeatInstance (runtime copy per flight instance).
 * Custom queries below support booking-service SeatClient lookups and
 * pessimistic locking during booking.confirmed status updates.
 */
public interface SeatInstanceRepository extends JpaRepository<SeatInstance, Long> {
    List<SeatInstance> findByFlightId(Long flightId);
    List<SeatInstance> findByFlightScheduleId(Long flightScheduleId);
    List<SeatInstance> findBySeatId(Long seatId);
    List<SeatInstance> findByFlightInstanceCabinId(Long id);

    /*
     * Returns bookable seats for a flight; used by GET /api/seat-instances/flight/{id}/available.
     */
    @Query("SELECT si FROM SeatInstance si WHERE si.flightId = :flightId AND si.status = 'AVAILABLE'")
    List<SeatInstance> findAvailableByFlightId(@Param("flightId") Long flightId);

    /*
     * Returns occupied seats for a flight; supports inventory reporting.
     */
    @Query("SELECT si FROM SeatInstance si WHERE si.flightId = :flightId AND si.status = 'OCCUPIED'")
    List<SeatInstance> findOccupiedByFlightId(@Param("flightId") Long flightId);

    Long countByFlightId(Long flightId);

    /*
     * Counts remaining inventory; exposed via GET .../flight/{id}/available/count.
     */
    @Query("SELECT COUNT(si) FROM SeatInstance si WHERE si.flightId = :flightId AND si.status = 'AVAILABLE'")
    Long countAvailableByFlightId(@Param("flightId") Long flightId);

    /*
     * Pessimistic lock for concurrent booking.confirmed updates.
     * JOIN FETCH loads seat and cabin in one round-trip.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SeatInstance si JOIN FETCH si.seat s JOIN FETCH si.flightInstanceCabin fc WHERE si.id = :seatInstanceId")
    Optional<SeatInstance> findByIdForUpdate(@Param("seatInstanceId") Long seatInstanceId);
}
