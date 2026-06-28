package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.model.FlightInstanceCabin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightInstanceCabinRepository extends JpaRepository<FlightInstanceCabin, Long> {
    Page<FlightInstanceCabin> findByFlightInstanceId(Long flightInstanceId, Pageable pageable);

    @Query("SELECT fic FROM FlightInstanceCabin fic WHERE fic.flightInstanceId = :flightInstanceId AND fic.cabinClass.name = :cabinClass")
    Optional<FlightInstanceCabin> findByFlightInstanceIdAndCabinClassName(
            @Param("flightInstanceId") Long flightInstanceId,
            @Param("cabinClass") CabinClassType cabinClass);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fc FROM FlightInstanceCabin fc WHERE fc.id = :id")
    Optional<FlightInstanceCabin> findByIdForUpdate(@Param("id") Long id);

    FlightInstanceCabin findByFlightInstanceIdAndCabinClassId(Long flightInstanceId, Long cabinClassId);
}
