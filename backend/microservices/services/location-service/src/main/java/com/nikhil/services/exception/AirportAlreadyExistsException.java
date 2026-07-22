package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

/**
 * Thrown when attempting to create an airport with an IATA code
 * that already exists.
 */
public class AirportAlreadyExistsException extends ConflictException {

    public AirportAlreadyExistsException(String iataCode) {
        super(
                ErrorCode.AIRPORT_ALREADY_EXISTS,
                "Airport with IATA code '" + iataCode + "' already exists."
        );
    }
}