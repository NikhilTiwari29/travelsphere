package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class FlightAlreadyExistsException extends ConflictException {

    public FlightAlreadyExistsException(String flightNumber) {
        super(
                ErrorCode.FLIGHT_ALREADY_EXISTS,
                "Flight with number '" + flightNumber + "' already exists."
        );
    }
}