package com.nikhil.services.repository;

import com.nikhil.services.model.SeatMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatMapRepository extends JpaRepository<SeatMap, Long> {
    List<SeatMap> findByAirlineId(Long airlineId);
    SeatMap findByCabinClassId(Long cabinClassId);
    boolean existsByAirlineIdAndCabinClassIdAndName(Long airlineId, Long cabinClassId, String name);
    boolean existsByAirlineIdAndNameAndIdNot(Long airlineId, String name, Long id);
    boolean existsByCabinClassId(Long cabinClassId);
    boolean existsByCabinClassIdAndIdNot(Long cabinClassId,Long id);


    @Query("SELECT sm FROM SeatMap sm LEFT JOIN FETCH sm.cabinClass WHERE sm.id = :id")
    Optional<SeatMap> findByIdWithDetails(@Param("id") Long id);



    @Query("SELECT sm FROM SeatMap sm LEFT JOIN FETCH sm.seats WHERE sm.id = :id")
    Optional<SeatMap> findByIdWithSeats(@Param("id") Long id);


}
