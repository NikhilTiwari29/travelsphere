package com.nikhil.services.repository;

import com.nikhil.services.model.City;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


/**
 * Repository for City database operations.
 *
 * Provides city lookup, duplicate checks, country/region filtering,
 * and keyword-based search.
 */
public interface CityRepository extends JpaRepository<City, Long> {


    // Finds a city by its exact name.
    Optional<City> findByName(String name);


    // Finds a city by its unique city code, e.g. BOM, DEL, BLR.
    Optional<City> findByCityCode(String cityCode);


    // Checks whether a city code already exists.
    boolean existsByCityCode(String cityCode);


    /*
     * Checks whether the requested city code is used by any record
     * other than the city currently being updated.
     *
     * Example: Mumbai ID 3 currently has code BOM.
     *
     * Updating ID 3 with BOM → allowed because BOM belongs to ID 3 itself.
     * Updating ID 3 with BLR → rejected if another record already uses BLR.
     */
    boolean existsByCityCodeAndIdNot(
            String cityCode,
            Long id
    );


    // Returns paginated cities belonging to the given country code.
    Page<City> findByCountryCodeIgnoreCase(
            String countryCode,
            Pageable pageable
    );


    // Finds cities whose country name contains the given text.
    Page<City> findByCountryNameContainingIgnoreCase(
            String countryName,
            Pageable pageable
    );


    // Returns paginated cities belonging to the given region.
    Page<City> findByRegionCodeIgnoreCase(
            String regionCode,
            Pageable pageable
    );


    // Returns all cities of a country sorted alphabetically by city name.
    List<City> findByCountryCodeIgnoreCaseOrderByNameAsc(
            String countryCode
    );


    /*
     * Searches across city name, city code, country code,
     * country name, and region code using partial matching.
     *
     * Example: keyword "Kolka" matches city name "Kolkata".
     */
    @Query("""
           SELECT c FROM City c
           WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.cityCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.countryCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.countryName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(c.regionCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
           """)
    Page<City> searchByKeyword(
            String keyword,
            Pageable pageable
    );
}