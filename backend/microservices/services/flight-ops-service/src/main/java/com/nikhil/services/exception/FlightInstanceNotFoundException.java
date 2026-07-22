package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class FlightInstanceNotFoundException
        extends ResourceNotFoundException {

    public FlightInstanceNotFoundException(Long id) {
        super(
                ErrorCode.FLIGHT_INSTANCE_NOT_FOUND,
                "Flight instance with id '" + id + "' was not found."
        );
    }
}