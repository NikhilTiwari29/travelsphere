package com.nikhil.services.repository;

import com.nikhil.services.model.FareRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FareRulesRepository extends JpaRepository<FareRules, Long> {

    Optional<FareRules> findByFareId(Long fareId);
    List<FareRules> findByAirlineId(Long airlineId);
    boolean existsByFareId(Long fareId);
}
