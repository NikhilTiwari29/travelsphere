package com.nikhil.services.repository;

import com.nikhil.services.model.Passenger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JPA repository for {@link Passenger}; custom queries below support profile lookup and admin search. */
@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    List<Passenger> findByPrimaryUserId(Long primaryUserId);

    Optional<Passenger> findByEmailAndPhoneAndDateOfBirth(
            String email, String phone, LocalDate dateOfBirth);

    Optional<Passenger> findByPassportNumber(String passportNumber);

    List<Passenger> findByEmail(String email);

    List<Passenger> findByLastNameContainingIgnoreCase(String lastName);

    /** Case-insensitive partial match on first or last name (admin / support search). */
    @Query("SELECT p FROM Passenger p WHERE " +
            "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR " +
            "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Passenger> findByNameContaining(@Param("name") String name);

    long countByPrimaryUserId(Long primaryUserId);

    Page<Passenger> findByPrimaryUserId(Long primaryUserId, Pageable pageable);

    /** Returns only active passenger profiles (soft-delete guard for saved-traveller lists). */
    @Query("SELECT p FROM Passenger p WHERE p.isActive = true")
    List<Passenger> findAllActive();
}
