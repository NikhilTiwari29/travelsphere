package com.nikhil.common_lib.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-passenger slice embedded in {@link BookingConfirmedEvent#passengers}.
 *
 * Built by booking-service from Passenger and Ticket entities; consumed by
 * notification-service for ticket details in email/SMS templates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerNotificationData {

    private String firstName;
    private String lastName;
    private String ticketNumber;
    private String seatNumber;
    private String passportNumber;
    private String nationality;
    private String gender;
    private boolean adult;
    private String frequentFlyerNumber;
    private boolean requiresWheelchair;
    private String dietaryPreferences;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getPassengerType() {
        return adult ? "Adult" : "Child";
    }
}
