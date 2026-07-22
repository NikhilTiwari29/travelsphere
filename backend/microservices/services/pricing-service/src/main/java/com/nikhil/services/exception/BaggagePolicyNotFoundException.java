package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

public class BaggagePolicyNotFoundException extends ResourceNotFoundException {
    public BaggagePolicyNotFoundException(Long id) {
        super(
                ErrorCode.BAGGAGE_POLICY_NOT_FOUND,
                "Baggage Policy with id '" + id + "' was not found."
        );
    }
}
