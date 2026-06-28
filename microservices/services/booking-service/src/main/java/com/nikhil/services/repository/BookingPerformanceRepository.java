package com.nikhil.services.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingPerformanceRepository {

    // Booking Statistics
    Long countBookingsByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate);

    Double sumRevenueByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate);

    List<com.nikhil.services.model.Booking> findBookingsByFlightIdAndDateRange(
            Long flightId, LocalDateTime startDate, LocalDateTime endDate);
}
