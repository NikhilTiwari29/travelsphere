package com.nikhil.services.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;

@Entity
@Table(name = "cities", indexes = {
        @Index(name = "idx_city_code", columnList = "cityCode"),
        @Index(name = "idx_city_name", columnList = "name"),
        @Index(name = "idx_country_code", columnList = "countryCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "City name is required")
    @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "City code is required")
    @Size(max = 10)
    @Column(nullable = false, unique = true)
    private String cityCode;

    @NotBlank(message = "Country code is required")
    @Size(max = 5)
    @Column(nullable = false)
    private String countryCode;

    @NotBlank(message = "Country name is required")
    @Size(max = 100)
    @Column(nullable = false)
    private String countryName;

    @Size(max = 10)
    private String regionCode;

    @Column(name = "time_zone_id", length = 50)
    private String timeZoneId;

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
    public String getCurrentUtcOffset() {
        if (timeZoneId != null) {
            ZoneId zone = ZoneId.of(timeZoneId);
            return zone.getRules().getOffset(Instant.now()).toString();
        }
        return null;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getStandardUtcOffset() {
        if (timeZoneId != null) {
            ZoneId zone = ZoneId.of(timeZoneId);
            return zone.getRules().getStandardOffset(Instant.now()).toString();
        }
        return null;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean observesDaylightSaving() {
        if (timeZoneId != null) {
            ZoneId zone = ZoneId.of(timeZoneId);
            return !zone.getRules().getTransitionRules().isEmpty();
        }
        return false;
    }
}
