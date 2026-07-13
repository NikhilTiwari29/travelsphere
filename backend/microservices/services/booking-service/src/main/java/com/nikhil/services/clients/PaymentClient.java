package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.request.PaymentInitiateRequest;
import com.nikhil.common_lib.payload.response.PaymentDTO;
import com.nikhil.common_lib.payload.response.PaymentInitiateResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * Feign client used by Booking Service to initiate payment and read payment
 * status from Payment Service.
 */
@FeignClient(name = "payment-service", fallback = PaymentClientFallback.class)
public interface PaymentClient {

    /*
     * Called at end of createBooking. Payment Service persists PENDING payment
     * and returns Razorpay checkout URL; verify step emits Kafka events.
     */
    @PostMapping("/api/payments/initiate")
    PaymentInitiateResponse initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest request,
            @RequestHeader("X-User-Id") Long userId);

    @GetMapping("/api/payments/booking/{bookingId}")
    PaymentDTO getPaymentByBookingId(@PathVariable Long bookingId);

    @PostMapping("/api/payments/batch/bookings")
    Map<Long, PaymentDTO> getPaymentsByBookingIds(@RequestBody List<Long> bookingIds);
}
