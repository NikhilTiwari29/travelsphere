package com.nikhil.service.service;

import com.nikhil.common_lib.event.BookingConfirmedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
 * Sends HTML booking confirmation emails from BookingConfirmedEvent payload.
 * Data is fully enriched upstream by Booking Service before Kafka publish.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.from-email}")
    private String fromEmail;

    @Value("${notification.from-name}")
    private String fromName;

    private static final DateTimeFormatter DATE_FMT   = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DT_FMT     = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    /*
     * Renders Thymeleaf template and sends via SMTP. Invoked from
     * BookingNotificationListener after booking.confirmed is consumed.
     */
    public void sendBookingConfirmation(BookingConfirmedEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(event.getContactEmail());
        helper.setSubject(buildSubject(event));
        helper.setText(buildHtmlBody(event), true);   // true = isHtml

        mailSender.send(message);
    }

    private String buildSubject(BookingConfirmedEvent event) {
        String depDate = event.getDepartureDateTime() != null
                ? event.getDepartureDateTime().format(DATE_FMT) : "";
        return String.format("Booking Confirmed | %s | %s\u2192%s | %s",
                event.getBookingReference(),
                event.getDepartureAirportCode(),
                event.getArrivalAirportCode(),
                depDate);
    }

    private String buildHtmlBody(BookingConfirmedEvent event) {
        Context ctx = new Context(Locale.ENGLISH);

        // Scalars
        ctx.setVariable("event", event);
        ctx.setVariable("passengerCount",
                event.getPassengers() != null ? event.getPassengers().size() : 1);

        // Formatted dates / times
        ctx.setVariable("depDate",
                event.getDepartureDateTime() != null ? event.getDepartureDateTime().format(DATE_FMT) : "N/A");
        ctx.setVariable("depTime",
                event.getDepartureDateTime() != null ? event.getDepartureDateTime().format(TIME_FMT) : "N/A");
        ctx.setVariable("arrDate",
                event.getArrivalDateTime() != null ? event.getArrivalDateTime().format(DATE_FMT) : "N/A");
        ctx.setVariable("arrTime",
                event.getArrivalDateTime() != null ? event.getArrivalDateTime().format(TIME_FMT) : "N/A");
        ctx.setVariable("paidAt",
                event.getPaidAt() != null ? event.getPaidAt().format(DT_FMT) : "N/A");
        ctx.setVariable("bookingDate",
                event.getBookingDate() != null ? event.getBookingDate().format(DT_FMT) : "N/A");

        // Fare breakdown helpers
        double base       = orZero(event.getBaseFare());
        double taxes      = orZero(event.getTaxesAndFees());
        double seats      = orZero(event.getSeatFees());
        double ancillary  = orZero(event.getAncillaryFees());
        double meals      = orZero(event.getMealFees());
        double total      = orZero(event.getTotalAmount());

        ctx.setVariable("baseFareTotal",  fmt(base));
        ctx.setVariable("taxes",          fmt(taxes));
        ctx.setVariable("seatFees",       fmt(seats));
        ctx.setVariable("ancillaryFees",  fmt(ancillary));
        ctx.setVariable("mealFees",       fmt(meals));
        ctx.setVariable("totalAmount",    fmt(total));

        // Baggage helpers
        ctx.setVariable("hasBaggage",
                event.getCheckinBaggagePieces() != null || event.getCabinBaggagePieces() != null);
        ctx.setVariable("checkinBaggage", baggageLabel(
                event.getCheckinBaggagePieces(), event.getCheckinBaggageWeightPerPiece()));
        ctx.setVariable("cabinBaggage", baggageLabel(
                event.getCabinBaggagePieces(), event.getCabinBaggageWeightPerPiece()));

        // Cabin class display name
        ctx.setVariable("cabinClassDisplay", cabinDisplayName(event.getCabinClass()));

        return templateEngine.process("email/booking-confirmation", ctx);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double orZero(Double v) {
        return v != null ? v : 0.0;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static String baggageLabel(Integer pieces, Double weightPer) {
        if (pieces == null && weightPer == null) return "Not included";
        if (pieces != null && weightPer != null)
            return pieces + " \u00d7 " + weightPer.intValue() + " kg";
        if (pieces != null) return pieces + " piece(s)";
        return weightPer.intValue() + " kg";
    }

    private static String cabinDisplayName(String cabinClass) {
        if (cabinClass == null) return "Economy";
        return switch (cabinClass) {
            case "ECONOMY"         -> "Economy";
            case "PREMIUM_ECONOMY" -> "Premium Economy";
            case "BUSINESS"        -> "Business";
            case "FIRST"           -> "First Class";
            default                -> cabinClass;
        };
    }
}
