package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.PaymentStatus;
import com.nikhil.services.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/*
 * Spring Data JPA repository for Payment rows.
 * Custom finders support idempotent initiate and batch lookup for Booking Service.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Used on initiate to reject duplicate payments for the same booking. */
    Optional<Payment> findByBookingId(Long bookingId);

    /** Lookup by Razorpay receipt / internal transaction id during verify. */
    Optional<Payment> findByTransactionId(String transactionId);

    /** Admin or ops queries filtered by payment lifecycle state. */
    List<Payment> findByStatus(PaymentStatus status);

    /** Batch fetch for Booking Service Feign: GET payments by booking id list. */
    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);
}
