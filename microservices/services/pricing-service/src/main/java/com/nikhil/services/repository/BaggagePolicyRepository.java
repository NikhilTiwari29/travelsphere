package com.nikhil.services.repository;

import com.nikhil.services.model.BaggagePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BaggagePolicyRepository extends JpaRepository<BaggagePolicy, Long> {

    Optional<BaggagePolicy> findByFareId(Long fareId);
    List<BaggagePolicy> findByAirlineId(Long airlineId);
    boolean existsByFareId(Long fareId);

    @Query("SELECT b.fare.id FROM BaggagePolicy b WHERE b.fare.id IN :fareIds")
    Set<Long> findFareIdsWithExistingPolicy(@Param("fareIds") Collection<Long> fareIds);
}
