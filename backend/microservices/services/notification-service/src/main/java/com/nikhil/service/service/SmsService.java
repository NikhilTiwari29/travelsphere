package com.nikhil.service.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.nikhil.common_lib.event.BookingConfirmedEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
 * Twilio SMS adapter for booking confirmation.
 * Same BookingConfirmedEvent as EmailService; gated by twilio.enabled flag.
 */
@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    private static final DateTimeFormatter SMS_DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);

    @PostConstruct
    public void init() {
        if (enabled && accountSid != null && !accountSid.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS service initialised");
        } else {
            log.info("Twilio SMS disabled — set twilio.enabled=true and credentials to activate");
        }
    }

    /*
     * Sends compact confirmation SMS when Twilio is enabled and contactPhone
     * is present on the Kafka event from Booking Service.
     */
    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        if (!enabled) {
            log.debug("SMS skipped (disabled): booking={}", event.getBookingReference());
            return;
        }
        if (event.getContactPhone() == null || event.getContactPhone().isBlank()) {
            log.warn("No phone number for booking={}", event.getBookingReference());
            return;
        }

        String body = buildSmsBody(event);
        log.debug("Sending SMS to {} for booking={}", event.getContactPhone(), event.getBookingReference());

        Message.creator(
                new PhoneNumber(event.getContactPhone()),
                new PhoneNumber(fromNumber),
                body
        ).create();
    }

    // ── 160-char friendly message ─────────────────────────────────────────────
    private String buildSmsBody(BookingConfirmedEvent event) {
        int paxCount = event.getPassengers() != null ? event.getPassengers().size() : 1;
        String depStr = event.getDepartureDateTime() != null
                ? event.getDepartureDateTime().format(SMS_DT_FMT) : "N/A";
        String route  = event.getDepartureAirportCode() + "\u2192" + event.getArrivalAirportCode();
        String total  = event.getTotalAmount() != null
                ? String.format("%.0f", event.getTotalAmount()) : "N/A";
        String currency = event.getCurrency() != null ? event.getCurrency() : "INR";

        return String.format(
                "TravelSphere: Booking CONFIRMED! Ref: %s | %s %s | %s | %d pax | %s %s paid. " +
                "Check-in opens 48hrs before departure. Queries: support@TravelSphere.com",
                event.getBookingReference(),
                event.getFlightNumber() != null ? event.getFlightNumber() : "",
                route,
                depStr,
                paxCount,
                currency,
                total
        );
    }
}
