package com.nikhil.services.service.impl;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.enums.PaymentGateway;
import com.nikhil.common_lib.enums.PaymentStatus;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.payload.request.PaymentInitiateRequest;
import com.nikhil.common_lib.payload.request.PaymentVerifyRequest;
import com.nikhil.common_lib.payload.response.PaymentDTO;
import com.nikhil.common_lib.payload.response.PaymentInitiateResponse;
import com.nikhil.common_lib.payload.response.PaymentLinkResponse;
import com.nikhil.services.client.UserClient;
import com.nikhil.services.event.PaymentEventProducer;
import com.nikhil.services.mapper.PaymentMapper;
import com.nikhil.services.model.Payment;
import com.nikhil.services.repository.PaymentRepository;
import com.nikhil.services.service.PaymentService;
import com.nikhil.services.service.gateway.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Application service responsible for payment lifecycle orchestration.
 *
 * Responsibilities:
 *
 * 1. Create and persist Payment records.
 * 2. Initialize checkout with the configured payment gateway.
 * 3. Verify gateway payment results.
 * 4. Transition Payment status.
 * 5. Publish payment lifecycle events to Kafka.
 * 6. Provide Payment data for Booking Service enrichment.
 *
 * Main flow:
 *
 * Booking Service
 *        |
 *        | initiatePayment()
 *        v
 * Payment Service
 *        |
 *        +-- Create PENDING Payment
 *        |
 *        +-- Fetch customer information from User Service
 *        |
 *        +-- Create Razorpay payment link
 *        |
 *        v
 * Customer completes checkout
 *        |
 *        | verifyPayment()
 *        v
 * Payment Service
 *        |
 *        +-- Fetch payment state from gateway
 *        |
 *        +-- Validate payment status and amount
 *        |
 *        +-- Update Payment state
 *        |
 *        +-- Publish payment.completed or payment.failed
 *                    |
 *                    v
 *                  Kafka
 *                    |
 *                    v
 *              Booking Service
 *
 * Read operations use the class-level read-only transaction policy.
 * State-changing operations explicitly override it with write transactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final RazorpayService razorpayService;
    private final UserClient userClient;


    /**
     * Creates a local PENDING Payment and initializes checkout with the
     * configured payment gateway.
     *
     * The Payment record is persisted before the external gateway call so
     * that the internal payment ID can be included in gateway metadata and
     * later used to correlate the gateway payment with the local Payment.
     *
     * @param request payment initialization details
     * @return gateway checkout details
     * @throws PaymentException when payment initialization fails
     */
    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(
            PaymentInitiateRequest request
    ) throws PaymentException {

        log.info(
                "Starting payment initiation userId={} bookingId={} gateway={} amount={}",
                request.getUserId(),
                request.getBookingId(),
                request.getGateway(),
                request.getAmount()
        );

        try {

            /*
             * Prevent another successful payment from being initiated for a
             * Booking that has already been paid.
             *
             * A failed payment may be followed by another payment attempt,
             * depending on the retry policy of the application.
             */
            paymentRepository
                    .findByBookingId(request.getBookingId())
                    .ifPresent(existingPayment -> {

                        if (existingPayment.getStatus() == PaymentStatus.SUCCESS) {

                            log.warn(
                                    "Payment initiation rejected because booking is already paid bookingId={} paymentId={}",
                                    request.getBookingId(),
                                    existingPayment.getId()
                            );

                            throw new IllegalStateException(
                                    "Payment already completed for booking: "
                                            + request.getBookingId()
                            );
                        }
                    });


            /*
             * Create the local Payment before contacting the external gateway.
             *
             * The initial state is PENDING because no payment has yet been
             * confirmed by the gateway.
             */
            Payment payment = Payment.builder()
                    .userId(request.getUserId())
                    .bookingId(request.getBookingId())
                    .amount(request.getAmount())
                    .provider(request.getGateway())
                    .status(PaymentStatus.PENDING)
                    .transactionId(generateTransactionId())
                    .build();

            payment = paymentRepository.save(payment);

            log.info(
                    "Pending payment persisted paymentId={} bookingId={} transactionId={} gateway={}",
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getTransactionId(),
                    payment.getProvider()
            );


            /*
             * Build the common payment initiation response.
             *
             * Gateway-specific checkout information is populated below after
             * the selected payment provider has been initialized.
             */
            PaymentInitiateResponse response =
                    PaymentInitiateResponse.builder()
                            .paymentId(payment.getId())
                            .gateway(request.getGateway())
                            .transactionId(payment.getTransactionId())
                            .amount(request.getAmount())
                            .description(request.getDescription())
                            .success(true)
                            .message("Payment initiated successfully")
                            .build();


            /*
             * Razorpay checkout initialization.
             *
             * Customer information is retrieved from User Service through
             * Feign and supplied to the gateway integration when creating
             * the payment link.
             */
            if (request.getGateway() == PaymentGateway.RAZORPAY) {

                log.debug(
                        "Fetching customer information for Razorpay checkout paymentId={} userId={}",
                        payment.getId(),
                        payment.getUserId()
                );

                UserDTO user =
                        userClient.getUserById(
                                payment.getUserId()
                        );

                log.debug(
                        "Creating Razorpay payment link paymentId={} bookingId={}",
                        payment.getId(),
                        payment.getBookingId()
                );

                PaymentLinkResponse paymentLinkResponse =
                        razorpayService.createPaymentLink(
                                user,
                                payment
                        );

                response.setCheckoutUrl(
                        paymentLinkResponse.getPayment_link_url()
                );

                response.setRazorpayOrderId(
                        paymentLinkResponse.getPayment_link_id()
                );

                log.info(
                        "Razorpay checkout initialized paymentId={} bookingId={}",
                        payment.getId(),
                        payment.getBookingId()
                );

            } else if (request.getGateway() == PaymentGateway.STRIPE) {

                /*
                 * Placeholder until the dedicated Stripe integration is
                 * implemented.
                 */
                log.warn(
                        "Stripe payment requested but production Stripe integration is not implemented paymentId={}",
                        payment.getId()
                );

                String checkoutUrl =
                        "https://checkout.stripe.com/pay/"
                                + payment.getTransactionId();

                response.setCheckoutUrl(checkoutUrl);
            }


            log.info(
                    "Payment initiation completed paymentId={} bookingId={} gateway={}",
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getProvider()
            );

            return response;

        } catch (Exception exception) {

            log.error(
                    "Payment initiation failed userId={} bookingId={} gateway={} error={}",
                    request.getUserId(),
                    request.getBookingId(),
                    request.getGateway(),
                    exception.getMessage(),
                    exception
            );

            throw new PaymentException(
                    "Failed to initiate payment: "
                            + exception.getMessage()
            );
        }
    }


    /**
     * Verifies a completed gateway payment and updates the local Payment state.
     *
     * For Razorpay, the authoritative payment state is fetched directly from
     * the gateway instead of trusting payment status supplied by the client.
     *
     * Successful verification:
     *
     *     Gateway CAPTURED
     *          ↓
     *     Payment SUCCESS
     *          ↓
     *     payment.completed event
     *
     * Failed verification:
     *
     *     Gateway verification failure
     *          ↓
     *     Payment FAILED
     *          ↓
     *     payment.failed event
     *
     * @param request gateway payment verification details
     * @return updated Payment state
     * @throws PaymentException when verification cannot be completed
     */
    @Override
    @Transactional
    public PaymentDTO verifyPayment(
            PaymentVerifyRequest request
    ) throws PaymentException {

        log.info(
                "Starting payment verification gateway=RAZORPAY"
        );


        /*
         * Fetch payment information directly from Razorpay.
         *
         * The complete gateway response is intentionally not logged because
         * payment gateway payloads can contain customer and transaction data.
         */
        JSONObject paymentDetails =
                razorpayService.fetchPaymentDetails(
                        request.getRazorpayPaymentId()
                );


        /*
         * Extract the gateway payment state and amount.
         *
         * Razorpay represents monetary amounts in the smallest currency unit.
         * For INR, 100 paise equals 1 rupee.
         */
        String gatewayStatus =
                paymentDetails.optString("status");

        long gatewayAmountInPaise =
                paymentDetails.optLong("amount");

        double gatewayAmount =
                gatewayAmountInPaise / 100.0;


        /*
         * The internal Payment ID was stored in Razorpay notes when checkout
         * was created. It is used here to correlate the external payment with
         * the Payment record owned by this service.
         */
        JSONObject notes =
                paymentDetails.optJSONObject("notes");

        if (notes == null) {

            log.error(
                    "Payment verification failed because gateway metadata is missing"
            );

            throw new PaymentException(
                    "Payment metadata is missing from gateway response"
            );
        }

        String paymentIdValue =
                notes.optString("payment_id");

        if (paymentIdValue == null || paymentIdValue.isBlank()) {

            log.error(
                    "Payment verification failed because internal payment ID is missing from gateway metadata"
            );

            throw new PaymentException(
                    "Internal payment reference is missing"
            );
        }


        Long paymentId;

        try {

            paymentId =
                    Long.parseLong(paymentIdValue);

        } catch (NumberFormatException exception) {

            log.error(
                    "Invalid internal payment reference received from gateway"
            );

            throw new PaymentException(
                    "Invalid internal payment reference"
            );
        }


        /*
         * Load the Payment aggregate that corresponds to the gateway payment.
         */
        Payment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(
                                () -> new PaymentException(
                                        "Payment not found with ID: "
                                                + paymentId
                                )
                        );

        log.debug(
                "Payment record resolved for verification paymentId={} bookingId={} provider={} currentStatus={}",
                payment.getId(),
                payment.getBookingId(),
                payment.getProvider(),
                payment.getStatus()
        );


        /*
         * Make verification idempotent.
         *
         * If the same verification callback is processed more than once after
         * the Payment has already succeeded, return the current state instead
         * of publishing payment.completed repeatedly.
         */
        if (payment.getStatus() == PaymentStatus.SUCCESS) {

            log.info(
                    "Payment already verified successfully paymentId={} bookingId={}",
                    payment.getId(),
                    payment.getBookingId()
            );

            return PaymentMapper.toDTO(payment);
        }


        /*
         * A Razorpay payment is accepted only when:
         *
         * 1. The gateway reports CAPTURED status.
         * 2. The amount returned by the gateway matches the expected amount
         *    stored in the local Payment record.
         *
         * Amount verification prevents a valid but incorrect payment amount
         * from confirming the Booking.
         */
        boolean statusValid =
                "captured".equalsIgnoreCase(gatewayStatus);

        boolean amountValid =
                Double.compare(
                        payment.getAmount(),
                        gatewayAmount
                ) == 0;

        boolean isValid =
                statusValid && amountValid;


        log.info(
                "Gateway verification result paymentId={} bookingId={} statusValid={} amountValid={}",
                payment.getId(),
                payment.getBookingId(),
                statusValid,
                amountValid
        );


        if (payment.getProvider() == PaymentGateway.RAZORPAY
                && isValid) {

            /*
             * Store the provider payment identifier for reconciliation,
             * support investigation, and gateway transaction lookup.
             */
            payment.setProviderPaymentId(
                    request.getRazorpayPaymentId()
            );
        }


        if (isValid) {

            /*
             * Mark the Payment successful before publishing the completion
             * event.
             */
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());

            payment =
                    paymentRepository.save(payment);

            log.info(
                    "Payment verified successfully paymentId={} bookingId={} transactionId={}",
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getTransactionId()
            );


            /*
             * Publish the successful payment event.
             *
             * Booking Service consumes this event and continues the booking
             * confirmation workflow.
             */
            paymentEventProducer.sendPaymentCompleted(payment);

            log.info(
                    "Payment completed event submitted paymentId={} bookingId={}",
                    payment.getId(),
                    payment.getBookingId()
            );

        } else {

            /*
             * Persist the failed state before publishing the failure event so
             * the database reflects the payment outcome.
             */
            payment.setStatus(PaymentStatus.FAILED);

            if (!statusValid) {

                payment.setFailureReason(
                        "Gateway payment status is not captured"
                );

            } else {

                payment.setFailureReason(
                        "Gateway payment amount does not match expected amount"
                );
            }

            payment =
                    paymentRepository.save(payment);

            log.warn(
                    "Payment verification failed paymentId={} bookingId={} statusValid={} amountValid={}",
                    payment.getId(),
                    payment.getBookingId(),
                    statusValid,
                    amountValid
            );


            /*
             * Publish payment failure so downstream consumers can apply their
             * failure-handling workflow.
             */
            paymentEventProducer.sendPaymentFailed(payment);

            log.info(
                    "Payment failed event submitted paymentId={} bookingId={}",
                    payment.getId(),
                    payment.getBookingId()
            );
        }


        return PaymentMapper.toDTO(payment);
    }


    /**
     * Retrieves all Payments using database-level pagination and sorting.
     */
    @Override
    public Page<PaymentDTO> getAllPayments(
            Pageable pageable
    ) {

        log.debug(
                "Retrieving payments page={} size={} sort={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        Page<PaymentDTO> payments =
                paymentRepository
                        .findAll(pageable)
                        .map(PaymentMapper::toDTO);

        log.debug(
                "Payments retrieved page={} returnedElements={} totalElements={} totalPages={}",
                payments.getNumber(),
                payments.getNumberOfElements(),
                payments.getTotalElements(),
                payments.getTotalPages()
        );

        return payments;
    }


    /**
     * Retrieves Payments for multiple Booking IDs using one database query.
     *
     * This method supports Booking Service batch enrichment and avoids making
     * one Payment Service request for every Booking.
     *
     * The returned map uses:
     *
     *     bookingId -> PaymentDTO
     */
    @Override
    public Map<Long, PaymentDTO> getPaymentsByBookingIds(
            List<Long> bookingIds
    ) {

        if (bookingIds == null || bookingIds.isEmpty()) {

            log.debug(
                    "Skipping batch payment lookup because booking ID list is empty"
            );

            return Map.of();
        }

        log.debug(
                "Retrieving payments for booking batch bookingCount={}",
                bookingIds.size()
        );

        Map<Long, PaymentDTO> payments =
                paymentRepository
                        .findByBookingIdIn(bookingIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Payment::getBookingId,
                                        PaymentMapper::toDTO
                                )
                        );

        log.debug(
                "Batch payment lookup completed requestedBookingCount={} resolvedPaymentCount={}",
                bookingIds.size(),
                payments.size()
        );

        return payments;
    }


    /**
     * Generates an internal transaction correlation identifier.
     *
     * Format:
     *
     *     TXN_<timestamp>_<random-suffix>
     *
     * Example:
     *
     *     TXN_1783662549123_A7F31C9D
     *
     * The timestamp improves operational traceability while the random UUID
     * fragment significantly reduces collision probability.
     *
     * Database uniqueness should still be enforced on the transactionId
     * column because application-generated identifiers must not rely only on
     * probabilistic uniqueness.
     */
    private String generateTransactionId() {

        return "TXN_"
                + System.currentTimeMillis()
                + "_"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }
}