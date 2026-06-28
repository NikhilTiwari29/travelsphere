package com.nikhil.services.repository;

import com.nikhil.services.model.Seat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findBySeatMapId(Long seatMapId);
    List<Seat> findByCabinClassId(Long cabinClassId);
    Optional<Seat> findBySeatNumberAndSeatMapId(String seatNumber, Long seatMapId);

    @Query("SELECT s FROM Seat s WHERE s.seatMap.id = :seatMapId AND s.isAvailable = true AND s.isActive = true AND s.isBlocked = false")
    List<Seat> findAvailableSeatsBySeatMapId(@Param("seatMapId") Long seatMapId);

    @Query("SELECT s FROM Seat s WHERE s.seatMap.id = :seatMapId AND (s.hasExtraLegroom = true OR s.isEmergencyExit = true OR s.hasExtraWidth = true)")
    List<Seat> findPremiumSeatsBySeatMapId(@Param("seatMapId") Long seatMapId);

    boolean existsBySeatMapId(Long seatMapId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Seat s WHERE s.seatMap.id = :seatMapId")
    void deleteBySeatMapId(@Param("seatMapId") Long seatMapId);

    boolean existsBySeatNumberAndSeatMapId(String seatNumber, Long seatMapId);
    Page<Seat> findBySeatMapId(Long seatMapId, Pageable pageable);
    long countBySeatMapId(Long seatMapId);
    long countBySeatMapIdAndIsAvailableTrue(Long seatMapId);
}
