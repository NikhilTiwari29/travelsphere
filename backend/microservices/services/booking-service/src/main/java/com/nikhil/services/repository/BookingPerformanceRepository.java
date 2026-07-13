package com.nikhil.services.repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Custom analytics queries for airline booking performance (Criteria API implementation below).
 *
 * Used by airline dashboards — not exposed via standard derived-query naming; implemented in
 * {@link BookingPerformanceRepositoryImpl} and mixed into {@link BookingRepository}.
 */
public interface BookingPerformanceRepository {

    // Booking Statistics
    Long countBookingsByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate);

    Double sumRevenueByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate);

    List<com.nikhil.services.model.Booking> findBookingsByFlightIdAndDateRange(
            Long flightId, LocalDateTime startDate, LocalDateTime endDate);
}
