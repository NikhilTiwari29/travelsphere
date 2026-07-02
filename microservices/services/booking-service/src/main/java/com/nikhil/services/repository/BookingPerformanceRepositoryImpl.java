package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.enums.PaymentStatus;
import com.nikhil.services.model.Booking;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Criteria API implementation of {@link BookingPerformanceRepository} airline analytics queries. */
@Repository
public class BookingPerformanceRepositoryImpl implements BookingPerformanceRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Counts non-cancelled bookings for a flight template within a bookingDate window. */
    @Override
    public Long countBookingsByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Booking> booking = query.from(Booking.class);

        query.select(cb.count(booking));

        Predicate flightPredicate = cb.equal(booking.get("flightId"), flightId);
        Predicate datePredicate = cb.between(booking.get("bookingDate"), startDate, endDate);
        Predicate notCancelled = cb.notEqual(booking.get("status"), BookingStatus.CANCELLED);

        query.where(cb.and(flightPredicate, datePredicate, notCancelled));

        return entityManager.createQuery(query).getSingleResult();
    }

    /** Sums successful payment amounts joined from Booking.payment for revenue reporting. */
    @Override
    public Double sumRevenueByFlightIdAndDateRange(Long flightId, LocalDateTime startDate, LocalDateTime endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Double> query = cb.createQuery(Double.class);
        Root<Booking> booking = query.from(Booking.class);

        Join<Object, Object> payment = booking.join("payment");

        query.select(cb.coalesce(cb.sum(payment.get("amount")), 0.0));

        Predicate flightPredicate = cb.equal(booking.get("flightId"), flightId);
        Predicate datePredicate = cb.between(booking.get("bookingDate"), startDate, endDate);
        Predicate notCancelled = cb.notEqual(booking.get("status"), BookingStatus.CANCELLED);
        Predicate paymentSuccess = cb.equal(payment.get("status"), PaymentStatus.SUCCESS);

        query.where(cb.and(flightPredicate, datePredicate, notCancelled, paymentSuccess));

        return entityManager.createQuery(query).getSingleResult();
    }

    /** Lists non-cancelled bookings with payment fetched, ordered by bookingDate ascending. */
    @Override
    public List<Booking> findBookingsByFlightIdAndDateRange(
            Long flightId, LocalDateTime startDate, LocalDateTime endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Booking> query = cb.createQuery(Booking.class);
        Root<Booking> booking = query.from(Booking.class);

        booking.fetch("payment", JoinType.LEFT);

        query.select(booking);

        Predicate flightPredicate = cb.equal(booking.get("flightId"), flightId);
        Predicate datePredicate = cb.between(booking.get("bookingDate"), startDate, endDate);
        Predicate notCancelled = cb.notEqual(booking.get("status"), BookingStatus.CANCELLED);

        query.where(cb.and(flightPredicate, datePredicate, notCancelled));
        query.orderBy(cb.asc(booking.get("bookingDate")));

        return entityManager.createQuery(query).getResultList();
    }
}
