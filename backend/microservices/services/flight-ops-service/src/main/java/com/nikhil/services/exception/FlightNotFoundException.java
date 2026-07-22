package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class FlightNotFoundException extends ResourceNotFoundException {

    public FlightNotFoundException(Long id) {
        super(
                ErrorCode.FLIGHT_NOT_FOUND,
                "Flight with id '" + id + "' was not found."
        );
    }

    public FlightNotFoundException(String flightNumber) {
        super(
                ErrorCode.FLIGHT_NOT_FOUND,
                "Flight with number '" + flightNumber + "' was not found."
        );
    }
}