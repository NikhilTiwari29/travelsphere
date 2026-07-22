package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class AircraftAlreadyExistsException extends ConflictException {

    public AircraftAlreadyExistsException(String code) {
        super(
                ErrorCode.AIRCRAFT_ALREADY_EXISTS,
                "Aircraft with code '" + code + "' already exists."
        );
    }
}