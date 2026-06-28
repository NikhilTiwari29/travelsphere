package com.nikhil.services.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nikhil.common_lib.embeddable.Address;
import com.nikhil.common_lib.embeddable.Analytics;
import com.nikhil.common_lib.embeddable.GeoCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZoneId;

@Entity
@Table(name = "airports", indexes = {
        @Index(name = "idx_airport_iata", columnList = "iataCode"),
        @Index(name = "idx_airport_city_id", columnList = "city_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "city"})
public class Airport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(nullable = false, length = 3, unique = true)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String iataCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "time_zone_id", length = 50)
    private String timeZoneId;

    @Embedded
    private Address address;

    // Same bounded context — direct JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private City city;

    @Embedded
    private GeoCode geoCode;

    @Embedded
    private Analytics analytics;

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ZoneId getTimeZone() {
        return timeZoneId != null ? ZoneId.of(timeZoneId) : null;
    }

    public void setTimeZone(ZoneId zoneId) {
        this.timeZoneId = zoneId != null ? zoneId.getId() : null;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getDetailedName() {
        if (city != null && city.getCountryCode() != null) {
            return name.toUpperCase() + "/" + city.getCountryCode();
        }
        return name.toUpperCase();
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCityName() {
        return city != null ? city.getName() : null;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCountryName() {
        return city != null ? city.getCountryName() : null;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCountryCode() {
        return city != null ? city.getCountryCode() : null;
    }
}
