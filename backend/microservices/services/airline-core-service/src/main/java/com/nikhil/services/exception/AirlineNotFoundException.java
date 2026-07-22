package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class AirlineNotFoundException extends ResourceNotFoundException {

    public AirlineNotFoundException(Long ownerId) {
        super(
                ErrorCode.AIRLINE_NOT_FOUND,
                "No airline found for owner id '" + ownerId + "'."
        );
    }
}