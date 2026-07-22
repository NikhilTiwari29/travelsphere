package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class AirlineAlreadyExistsException extends ConflictException {

    public AirlineAlreadyExistsException(String code) {
        super(
                ErrorCode.AIRLINE_ALREADY_EXISTS,
                "Airline with code '" + code + "' already exists."
        );
    }
}