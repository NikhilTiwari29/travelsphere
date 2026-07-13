package com.nikhil.common_lib.enums;

/**
 * Lifecycle state of a booking in booking-service.
 *
 * PENDING — created, awaiting payment; CONFIRMED — payment.completed received;
 * CANCELLED — payment.failed or explicit cancel; COMPLETED — flight has occurred.
 */
public enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED, COMPLETED
}
