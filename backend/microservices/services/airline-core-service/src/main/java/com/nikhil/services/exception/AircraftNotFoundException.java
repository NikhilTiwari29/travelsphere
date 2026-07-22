package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class AircraftNotFoundException extends ResourceNotFoundException {

    public AircraftNotFoundException(Long id) {
        super(
                ErrorCode.AIRCRAFT_NOT_FOUND,
                "Aircraft with id '" + id + "' was not found."
        );
    }
}