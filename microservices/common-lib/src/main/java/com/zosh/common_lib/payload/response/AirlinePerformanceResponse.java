package com.nikhil.common_lib.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlinePerformanceResponse {
    private List<AirlineStatistics> topAirlinesByBookings;
    private List<AirlineStatistics> topAirlinesByRevenue;
    private List<AirlineStatistics> topAirlinesByAverageRevenue;
    private List<AirlineStatistics> topAirlinesByFlightCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AirlineStatistics {
        private Long airlineId;
        private String airlineName;
        private String airlineCode;
        private String country;
        private String logoUrl;
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageRevenuePerBooking;
        private Long totalFlights;
        private Long totalRoutes;
        private Double bookingGrowthRate;
        private String status;
    }
}
