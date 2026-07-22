package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class FareAlreadyExistsException extends ConflictException {
    public FareAlreadyExistsException(Long id) {
        super(
                ErrorCode.FARE_ALREADY_EXISTS,
                "Fare with ID '" + id + "' already exists."
        );
    }

    public FareAlreadyExistsException(String name) {
        super(
                ErrorCode.FARE_ALREADY_EXISTS,
                "Fare with name '" + name + "' already exists."
        );
    }
}
