package com.nikhil.services.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nikhil.common_lib.embeddable.Address;
import com.nikhil.common_lib.embeddable.Analytics;
import com.nikhil.common_lib.embeddable.GeoCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZoneId;


/**
 * Represents an airport in the location-service.
 *
 * Each airport has a unique IATA code and belongs to one city.
 * Address, geo-location, and analytics fields are stored as embedded
 * columns in the airports table.
 */
@Entity
@Table(name = "airports", indexes = {

        // Speeds up airport lookup by IATA code.
        @Index(name = "idx_airport_iata", columnList = "iataCode"),

        // Speeds up queries that find airports belonging to a city.
        @Index(name = "idx_airport_city_id", columnList = "city_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

// Equality is based only on fields explicitly marked with @Include.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

// toString() includes only explicitly selected fields.
@ToString(onlyExplicitlyIncluded = true)

// Prevents Hibernate proxy fields and the city relationship from being serialized.
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "city"})
public class Airport {


    // Auto-generated primary key.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;


    // Unique 3-letter IATA airport code, e.g. BOM, DEL, BLR.
    @Column(nullable = false, length = 3, unique = true)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String iataCode;


    // Official airport name.
    @Column(nullable = false, length = 255)
    private String name;


    /*
     * Stores the ZoneId as a String in the database.
     *
     * Example:
     * Asia/Kolkata
     * Europe/London
     */
    @Column(name = "time_zone_id", length = 50)
    private String timeZoneId;


    // Address fields are stored as columns in the airports table.
    @Embedded
    private Address address;


    /*
     * Airport and City belong to the same location-service bounded context,
     * so a direct JPA relationship is used instead of a service-to-service call.
     *
     * Many airports can belong to one city.
     *
     * Example:
     * City: London
     *   → Heathrow Airport
     *   → Gatwick Airport
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    @JsonIgnore
    private City city;


    // Latitude and longitude are embedded into the airports table.
    @Embedded
    private GeoCode geoCode;


    // Airport analytics fields are embedded into the airports table.
    @Embedded
    private Analytics analytics;


    /*
     * Converts the stored String timeZoneId into a ZoneId object.
     *
     * @Transient prevents JPA from treating this derived property
     * as a separate database column.
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
     * Returns a display-friendly airport name with country code.
     *
     * Example:
     * CHHATRAPATI SHIVAJI MAHARAJ INTERNATIONAL AIRPORT/IN
     */
    @Transient
    @JsonIgnore
    public String getDetailedName() {

        if (city != null && city.getCountryCode() != null) {
            return name.toUpperCase()
                    + "/"
                    + city.getCountryCode();
        }

        return name.toUpperCase();
    }


    // Convenience method to get the city name from the related City entity.
    @Transient
    @JsonIgnore
    public String getCityName() {
        return city != null
                ? city.getName()
                : null;
    }


    // Convenience method to get the country name from the related City entity.
    @Transient
    @JsonIgnore
    public String getCountryName() {
        return city != null
                ? city.getCountryName()
                : null;
    }


    // Convenience method to get the country code from the related City entity.
    @Transient
    @JsonIgnore
    public String getCountryCode() {
        return city != null
                ? city.getCountryCode()
                : null;
    }
}