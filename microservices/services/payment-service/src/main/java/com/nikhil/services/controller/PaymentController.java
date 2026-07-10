package com.nikhil.services.controller;

import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.payload.request.PaymentInitiateRequest;
import com.nikhil.common_lib.payload.request.PaymentVerifyRequest;
import com.nikhil.common_lib.payload.response.PaymentDTO;
import com.nikhil.common_lib.payload.response.PaymentInitiateResponse;
import com.nikhil.services.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for payment initiation, verification, and payment retrieval.
 *
 * Payment Service owns the Payment aggregate and coordinates payment processing
 * with the configured external payment gateway.
 *
 * Main payment flow:
 *
 * Booking Service
 *        |
 *        | POST /api/payments/initiate
 *        v
 * Payment Service
 *        |
 *        | Create local PENDING Payment
 *        | Create payment gateway checkout/order
 *        v
 * Frontend checkout
 *        |
 *        | POST /api/payments/verify
 *        v
 * Payment Service
 *        |
 *        | Verify payment with gateway
 *        | Update local Payment status
 *        | Publish payment.completed or payment.failed
 *        v
 * Kafka
 *        |
 *        v
 * Booking Service
 *        |
 *        +-- Confirm Booking on successful payment
 *        |
 *        +-- Handle failed payment workflow
 *
 * Base route:
 *
 *     /api/payments
 *
 * Sensitive payment credentials, signatures, tokens, and gateway secrets
 * must never be written to application logs.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;


    /**
     * Initiates payment processing for a Booking.
     *
     * This endpoint is called synchronously by Booking Service after the final
     * booking amount has been calculated from fare, seat, ancillary, and meal
     * selections.
     *
     * Payment Service creates the local Payment record and initializes the
     * external gateway checkout flow.
     *
     * @param request payment initialization details
     * @param userId authenticated user identifier propagated by the gateway
     * @return payment initialization response containing checkout information
     * @throws PaymentException when payment initialization fails
     */
    @PostMapping("/initiate")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) throws PaymentException {

        log.info(
                "Received payment initiation request userId={} bookingId={} gateway={}",
                userId,
                request.getBookingId(),
                request.getGateway()
        );

        /*
         * Delegate payment creation and external gateway initialization to the
         * service layer. Gateway-specific processing must remain outside the
         * controller.
         */
        PaymentInitiateResponse response =
                paymentService.initiatePayment(request);

        log.info(
                "Payment initiation completed userId={} bookingId={}",
                userId,
                request.getBookingId()
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Verifies the result of an external payment attempt.
     *
     * The frontend calls this endpoint after completing the payment gateway
     * checkout flow. Payment Service validates the payment result and updates
     * the local Payment state.
     *
     * A successful verification can trigger the payment completion event flow
     * consumed by Booking Service.
     *
     * Payment verification signatures and gateway credentials are intentionally
     * excluded from logs.
     *
     * @param request payment verification details received after checkout
     * @return verified Payment state
     * @throws PaymentException when verification fails
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentDTO> verifyPayment(
            @Valid @RequestBody PaymentVerifyRequest request
    ) throws PaymentException {

        log.info(
                "Received payment verification request"
        );

        /*
         * Payment verification is handled by the service layer because it
         * contains gateway-specific validation and Payment state transitions.
         */
        PaymentDTO payment =
                paymentService.verifyPayment(request);

        log.info(
                "Payment verification completed paymentId={} status={}",
                payment.getId(),
                payment.getStatus()
        );

        return ResponseEntity.ok(payment);
    }


    /**
     * Retrieves Payments for multiple Booking IDs in a single request.
     *
     * This batch endpoint avoids an N+1 cross-service call pattern when
     * Booking Service needs Payment information for multiple Bookings.
     *
     * Example:
     *
     * Booking IDs:
     *
     *     [101, 102, 103]
     *
     * Result:
     *
     *     bookingId -> PaymentDTO
     *
     * @param bookingIds Booking identifiers whose Payments are required
     * @return map keyed by Booking ID
     */
    @PostMapping("/batch/bookings")
    public ResponseEntity<Map<Long, PaymentDTO>> getPaymentsByBookingIds(
            @RequestBody List<Long> bookingIds
    ) {

        log.debug(
                "Received batch payment lookup request bookingCount={}",
                bookingIds.size()
        );

        Map<Long, PaymentDTO> payments =
                paymentService.getPaymentsByBookingIds(bookingIds);

        log.debug(
                "Batch payment lookup completed requestedBookingCount={} resolvedPaymentCount={}",
                bookingIds.size(),
                payments.size()
        );

        return ResponseEntity.ok(payments);
    }


    /**
     * Retrieves Payments using pagination and configurable sorting.
     *
     * Query parameters:
     *
     *     page          - zero-based page index
     *     size          - maximum records per page
     *     sortBy        - Payment property used for sorting
     *     sortDirection - ASC or DESC
     *
     * Example:
     *
     *     GET /api/payments?page=0&size=20
     *         &sortBy=createdAt&sortDirection=DESC
     *
     * @param page zero-based page index
     * @param size requested page size
     * @param sortBy entity property used for sorting
     * @param sortDirection requested sort direction
     * @param userId authenticated user identifier propagated by the gateway
     * @return paginated Payment records
     */
    @GetMapping
    public ResponseEntity<Page<PaymentDTO>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.debug(
                "Received payment list request userId={} page={} size={} sortBy={} sortDirection={}",
                userId,
                page,
                size,
                sortBy,
                sortDirection
        );

        /*
         * Convert the API sorting parameters into Spring Data pagination and
         * sorting abstractions before delegating the query to the service layer.
         */
        Sort.Direction direction =
                sortDirection.equalsIgnoreCase("ASC")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(direction, sortBy)
                );

        Page<PaymentDTO> payments =
                paymentService.getAllPayments(pageable);

        log.debug(
                "Payment list retrieved userId={} page={} returnedElements={} totalElements={} totalPages={}",
                userId,
                page,
                payments.getNumberOfElements(),
                payments.getTotalElements(),
                payments.getTotalPages()
        );

        return ResponseEntity.ok(payments);
    }
}