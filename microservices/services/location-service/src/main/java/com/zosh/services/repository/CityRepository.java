package com.nikhil.services.repository;

import com.nikhil.services.model.City;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByName(String name);

    Optional<City> findByCityCode(String cityCode);

    boolean existsByCityCode(String cityCode);

    boolean existsByCityCodeAndIdNot(String cityCode, Long id);

    Page<City> findByCountryCodeIgnoreCase(String countryCode, Pageable pageable);

    Page<City> findByCountryNameContainingIgnoreCase(String countryName, Pageable pageable);

    Page<City> findByRegionCodeIgnoreCase(String regionCode, Pageable pageable);

    List<City> findByCountryCodeIgnoreCaseOrderByNameAsc(String countryCode);

    @Query("""
           SELECT c FROM City c
           WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.cityCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.countryCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.countryName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.regionCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
           """)
    Page<City> searchByKeyword(String keyword, Pageable pageable);
}
