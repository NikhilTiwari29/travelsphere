package com.nikhil.common_lib.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analytics {

    @Column(name = "traveler_score")
    private Integer travelerScore;

    @Column(name = "annual_passengers")
    private Double annualPassengers;

    @Column(name = "destinations_count")
    private Integer destinationsCount;

    @Column(name = "size_category", length = 20)
    private String sizeCategory;

    @Column(name = "airlines_count")
    private Integer airlinesCount;

    @Column(name = "on_time_performance")
    private Double onTimePerformance;
}
