package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.services.model.Fare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FareRepository extends JpaRepository<Fare, Long> {

    List<Fare> findByFlightId(Long flightId);
    Page<Fare> findByFlightId(Long flightId, Pageable pageable);

    List<Fare> findByCabinClassId(Long cabinClassId);

    List<Fare> findByFlightIdAndCabinClassId(Long flightId, Long cabinClassId);

    @Query("SELECT f FROM Fare f LEFT JOIN FETCH f.fareRules LEFT JOIN FETCH f.baggagePolicy WHERE f.id = :id")
    Optional<Fare> findByIdWithDetails(@Param("id") Long id);

    @Query(""" 
        SELECT f FROM Fare f
        LEFT JOIN FETCH f.fareRules
        LEFT JOIN FETCH f.baggagePolicy
        WHERE f.flightId = :flightId
    """)
    List<Fare> findByFlightIdWithDetails(@Param("flightId") Long flightId);

    boolean existsByFlightIdAndCabinClassIdAndName(Long flightId, Long cabinClassId, String name);
    boolean existsByFlightIdAndCabinClassIdAndNameAndIdNot(Long flightId, Long cabinClassId, String name, Long id);

    /**
     * Batch query used by flight search: returns all fares for a set of flight IDs
     * filtered by cabin class type, so the search layer can apply price range filtering
     * without additional cross-service calls.
     */
    List<Fare> findByFlightIdInAndCabinClassId(Collection<Long> flightIds, Long cabinClassId);

    /**
     * Returns composite keys "flightId:cabinClassId:name" for existing fares
     * whose flightId is in the given collection. Used by bulk create to detect
     * duplicates with a single DB round-trip.
     */
    @Query("SELECT CONCAT(f.flightId, ':', f.cabinClassId, ':', f.name) FROM Fare f WHERE f.flightId IN :flightIds")
    Set<String> findExistingFareKeys(@Param("flightIds") Collection<Long> flightIds);
}
