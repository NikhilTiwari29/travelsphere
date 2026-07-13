package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.AirlineStatus;
import com.nikhil.services.model.Airline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

    Optional<Airline> findByOwnerId(Long ownerId);

    Optional<Airline> findByIataCode(String code);

    Optional<Airline> findByIcaoCode(String code);

    boolean existsByIataCode(String code);

    boolean existsByIcaoCode(String code);

    Page<Airline> findByCountryIgnoreCase(String country, Pageable pageable);

    Page<Airline> findByStatus(AirlineStatus status, Pageable pageable);

    List<Airline> findByStatus(AirlineStatus status);

    @Query("SELECT a FROM Airline a " +
            "WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.iataCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.icaoCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.country) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Airline> searchByKeyword(String keyword, Pageable pageable);

    /** Used for flight search: resolve IATA codes → airline IDs in bulk. */
    List<Airline> findAllByIataCodeIn(Collection<String> iataCodes);

    /** Used for flight search: find all airlines belonging to a given alliance. */
    List<Airline> findAllByAllianceIgnoreCase(String alliance);
}
