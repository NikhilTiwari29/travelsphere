package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class AirlineNotFoundException extends ResourceNotFoundException {

    /**
     * Airline not found by airline ID.
     */
    public AirlineNotFoundException(Long airlineId) {
        super(
                ErrorCode.AIRLINE_NOT_FOUND,
                "Airline with id '" + airlineId + "' was not found."
        );
    }

    /**
     * Airline not found for authenticated owner.
     */
    public AirlineNotFoundException(Long ownerId, boolean ownerLookup) {
        super(
                ErrorCode.AIRLINE_NOT_FOUND,
                "No airline found for owner id '" + ownerId + "'."
        );
    }
}