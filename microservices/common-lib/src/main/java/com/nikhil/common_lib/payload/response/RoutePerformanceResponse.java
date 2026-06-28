package com.nikhil.common_lib.payload.response;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePerformanceResponse {

    private List<RouteStatistics> topRoutesByBookings;
    private List<RouteStatistics> topRoutesByRevenue;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteStatistics {
        private String route; // e.g., "DEL - BOM"
        private String departureAirportCode;
        private String departureAirportName;
        private String arrivalAirportCode;
        private String arrivalAirportName;
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageRevenuePerBooking;
    }
}
