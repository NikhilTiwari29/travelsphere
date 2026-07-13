package com.nikhil.services.model;

import com.nikhil.common_lib.enums.PaymentGateway;
import com.nikhil.common_lib.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/*
 * JPA entity for the local payments table.
 *
 * One row per booking payment attempt. userId and bookingId are logical refs
 * to user-service and booking-service; status transitions are driven by
 * PaymentServiceImpl after Razorpay initiate/verify.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cross-service ref: User (user-service)
    @Column(name = "user_id")
    private Long userId;

    // Cross-service ref: Booking (booking-service)
    @Column(name = "booking_id")
    private Long bookingId;

    private Double amount;

    /** Razorpay (or future gateway) used for this payment. */
    @Enumerated(EnumType.STRING)
    private PaymentGateway provider;

    /** Gateway-side payment / order identifier returned by Razorpay. */
    private String providerPaymentId;
    /** Internal correlation id sent to Razorpay and stored on the payment link. */
    private String transactionId;
    /** Card/UPI/netbanking method reported by Razorpay on successful capture. */
    private String method;

    /** PENDING → SUCCESS | FAILED; consumed by Booking Service via Kafka events. */
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String failureReason;
    /** Set when status becomes SUCCESS after verify. */
    private LocalDateTime paidAt;
    private String refundId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
