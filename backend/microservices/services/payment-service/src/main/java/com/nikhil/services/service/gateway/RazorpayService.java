package com.nikhil.services.service.gateway;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.payload.response.PaymentLinkResponse;
import com.nikhil.services.model.Payment;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/*
 * External Razorpay gateway adapter used during payment initiation and verify.
 * Embeds payment_id in link notes so verify can map gateway payment → local row.
 */
@Service
@RequiredArgsConstructor
public class RazorpayService {

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.callback.base-url}")
    private String callbackBaseUrl;

    /*
     * Creates hosted checkout URL returned to Booking Service via initiatePayment.
     * Notes carry payment_id for the later verify → Kafka publish step.
     */
    public PaymentLinkResponse createPaymentLink(
            UserDTO user,
            Payment payment) throws PaymentException {

        if (!isConfigured()) throw new PaymentException("razorpay not configured. please setup api key and secret");

        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Convert amount to paisa (1 INR = 100 paisa)
            BigDecimal amount = BigDecimal.valueOf(payment.getAmount());
            Long amountInPaisa = amount.multiply(new BigDecimal("100")).longValue();


            JSONObject paymentLinkRequest = new JSONObject();
            paymentLinkRequest.put("amount", amountInPaisa);
            paymentLinkRequest.put("currency", "INR");
            paymentLinkRequest.put("description", payment.getTransactionId());

            // Customer details
            JSONObject customer = new JSONObject();
            customer.put("name", user.getFullName());
            customer.put("email", user.getEmail());
            if (user.getPhone() != null) {
                customer.put("contact", user.getPhone());
            }
            paymentLinkRequest.put("customer", customer);

            // Notification settings
            JSONObject notify = new JSONObject();
            notify.put("email", true);
            notify.put("sms", user.getPhone() != null);
            paymentLinkRequest.put("notify", notify);

            // Enable reminders
            paymentLinkRequest.put("reminder_enable", true);

            // Callback configuration
            String successUrl = callbackBaseUrl + "/booking-success/" + payment.getBookingId();
            String cancelUrl = callbackBaseUrl + "/payment-cancelled/" + payment.getId();

            paymentLinkRequest.put("callback_url", successUrl);
            paymentLinkRequest.put("callback_method", "get");

            // Additional metadata for tracking
            JSONObject notes = new JSONObject();
            notes.put("user_id", user.getId());
            notes.put("payment_id", payment.getId());
            notes.put("booking_id",payment.getBookingId());

            paymentLinkRequest.put("notes", notes);

            // Create payment link
            PaymentLink paymentLink = razorpay.paymentLink.create(paymentLinkRequest);

            String paymentUrl = paymentLink.get("short_url");
            String paymentLinkId = paymentLink.get("id");


            PaymentLinkResponse response = new PaymentLinkResponse();
            response.setPayment_link_url(paymentUrl);
            response.setPayment_link_id(paymentLinkId);

            return response;

        } catch ( RazorpayException e) {

            throw new PaymentException("Failed to create payment link: " + e.getMessage());
        }
    }

    /** Called from verifyPayment; status "captured" triggers payment.completed. */
    public JSONObject fetchPaymentDetails(String paymentId) throws PaymentException {
        if (!isConfigured()) throw new PaymentException("razorpay not configured. please setup api key and secret");



        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            com.razorpay.Payment payment = razorpay.payments.fetch(paymentId);

            return payment.toJson();

        } catch (RazorpayException e) {

            throw new PaymentException("Failed to fetch payment details: " + e.getMessage());
        }
    }


    public boolean isConfigured() {
        return razorpayKeyId != null && !razorpayKeyId.isEmpty()
                && razorpayKeySecret != null && !razorpayKeySecret.isEmpty();
    }


}
