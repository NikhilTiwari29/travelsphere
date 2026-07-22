package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

/**
 * Thrown when an airport cannot be found.
 */
public class AirportNotFoundException extends ResourceNotFoundException {

    public AirportNotFoundException(Long airportId) {
        super(
                ErrorCode.AIRPORT_NOT_FOUND,
                "Airport with id '" + airportId + "' was not found."
        );
    }

    public AirportNotFoundException(String iataCode) {
        super(
                ErrorCode.AIRPORT_NOT_FOUND,
                "Airport with IATA code '" + iataCode + "' was not found."
        );
    }
}