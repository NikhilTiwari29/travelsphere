package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

/**
 * Thrown when a user cannot be found.
 */
public class FareNotFoundException extends ResourceNotFoundException {

    public FareNotFoundException(Long id) {
        super(
                ErrorCode.USER_NOT_FOUND,
                "Fare with id '" + id + "' was not found."
        );
    }

    public FareNotFoundException(Long flightId, Long cabinClassId) {
        super(
                ErrorCode.FARE_NOT_FOUND,
                "Fare not found for flightId '" + flightId
                        + "' and cabinClassId '" + cabinClassId + "'."
        );
    }
}