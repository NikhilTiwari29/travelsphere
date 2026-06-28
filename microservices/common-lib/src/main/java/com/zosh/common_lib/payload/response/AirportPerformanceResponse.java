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
public class AirportPerformanceResponse {
    private List<AirportStatistics> topAirportsByBookings;
    private List<AirportStatistics> topAirportsByRevenue;
    private List<AirportStatistics> topDepartureAirports;
    private List<AirportStatistics> topArrivalAirports;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AirportStatistics {
        private String airportCode;
        private String airportName;
        private String city;
        private String country;
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageRevenuePerBooking;
        private Long totalFlights;
        private String performanceType;
    }
}
