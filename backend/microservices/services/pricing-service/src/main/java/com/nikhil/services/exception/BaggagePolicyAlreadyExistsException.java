package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class BaggagePolicyAlreadyExistsException extends ConflictException {
    public BaggagePolicyAlreadyExistsException(Long id) {
        super(
                ErrorCode.BAGGAGE_POLICY_ALREADY_EXISTS,
                "Baggage Policy with id '" + id + "' already exists."
        );
    }
}
