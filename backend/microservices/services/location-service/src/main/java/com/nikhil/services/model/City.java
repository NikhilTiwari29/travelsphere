package com.nikhil.services.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;


/**
 * Represents city reference data used by airports and location search.
 *
 * Stores the city's identification, country/region information,
 * and IANA timezone ID for dynamic UTC offset calculations.
 */
@Entity
@Table(name = "cities", indexes = {

        // Speeds up lookup by unique city code.
        @Index(name = "idx_city_code", columnList = "cityCode"),

        // Speeds up city-name searches.
        @Index(name = "idx_city_name", columnList = "name"),

        // Speeds up queries that filter cities by country.
        @Index(name = "idx_country_code", columnList = "countryCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City {


    // Auto-generated primary key.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // City name, e.g. Mumbai or Bengaluru.
    @NotBlank(message = "City name is required")
    @Size(max = 100)
    @Column(nullable = false)
    private String name;


    // Unique city code used for lookup, e.g. BOM, BLR, DEL.
    @NotBlank(message = "City code is required")
    @Size(max = 10)
    @Column(nullable = false, unique = true)
    private String cityCode;


    // Country code, e.g. IN, US, GB.
    @NotBlank(message = "Country code is required")
    @Size(max = 5)
    @Column(nullable = false)
    private String countryCode;


    // Full country name, e.g. India or United Kingdom.
    @NotBlank(message = "Country name is required")
    @Size(max = 100)
    @Column(nullable = false)
    private String countryName;


    // Optional state, province, or regional code, e.g. MH, KA, TN.
    @Size(max = 10)
    private String regionCode;


    /*
     * Stores the IANA timezone ID as a String.
     *
     * Example:
     * Asia/Kolkata
     * Europe/London
     * America/New_York
     */
    @Column(name = "time_zone_id", length = 50)
    private String timeZoneId;


    /*
     * Converts the stored timezone ID into a ZoneId object.
     * @Transient prevents JPA from creating another database column.
     */
    @Transient
    @JsonIgnore
    public ZoneId getTimeZone() {

        return timeZoneId != null
                ? ZoneId.of(timeZoneId)
                : null;
    }


    // Converts ZoneId to String before storing it in timeZoneId.
    public void setTimeZone(ZoneId zoneId) {

        this.timeZoneId = zoneId != null
                ? zoneId.getId()
                : null;
    }


    /*
     * Returns the city's current UTC offset.
     *
     * The value can change because of daylight saving time.
     *
     * Example:
     * London → +01:00 during summer
     * London → Z (UTC) during winter
     */
    @Transient
    @JsonIgnore
    public String getCurrentUtcOffset() {

        if (timeZoneId != null) {

            ZoneId zone = ZoneId.of(timeZoneId);

            return zone.getRules()
                    .getOffset(Instant.now())
                    .toString();
        }

        return null;
    }


    /*
     * Returns the standard UTC offset without daylight-saving adjustment.
     *
     * Example:
     * London standard offset → Z (UTC)
     */
    @Transient
    @JsonIgnore
    public String getStandardUtcOffset() {

        if (timeZoneId != null) {

            ZoneId zone = ZoneId.of(timeZoneId);

            return zone.getRules()
                    .getStandardOffset(Instant.now())
                    .toString();
        }

        return null;
    }


    /*
     * Checks whether the timezone has recurring daylight-saving rules.
     *
     * Example:
     * Europe/London → true
     * Asia/Kolkata   → false
     */
    @Transient
    @JsonIgnore
    public boolean observesDaylightSaving() {

        if (timeZoneId != null) {

            ZoneId zone = ZoneId.of(timeZoneId);

            return !zone.getRules()
                    .getTransitionRules()
                    .isEmpty();
        }

        return false;
    }
}