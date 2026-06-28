package com.nikhil.services.model;

import com.nikhil.common_lib.enums.FlightStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "flight_instances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "departure_date_time"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"flight"})
@EqualsAndHashCode(of = "id")
public class FlightInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service ref: Airline is in airline-core-service
    @Column(name = "airline_id")
    private Long airlineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    // Cross-service ref: Airport is in location-service
    @Column(name = "departure_airport_id", nullable = false)
    private Long departureAirportId;

    // Cross-service ref: Airport is in location-service
    @Column(name = "arrival_airport_id", nullable = false)
    private Long arrivalAirportId;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private LocalDateTime departureDateTime;

    @Column(nullable = false)
    private LocalDateTime arrivalDateTime;

    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightStatus status;

    private Integer minAdvanceBookingDays;
    private Integer maxAdvanceBookingDays;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    private String terminal;
    private String gate;

    @Version
    private Long version;

    @Transient
    public String getFormattedDuration() {
        if (departureDateTime == null || arrivalDateTime == null) {
            return null;
        }
        Duration duration = Duration.between(departureDateTime, arrivalDateTime);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("min");
        return sb.toString().trim();
    }


}
