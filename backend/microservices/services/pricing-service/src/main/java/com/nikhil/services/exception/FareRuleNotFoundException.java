package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

/**
 * Thrown when a user cannot be found.
 */
public class FareRuleNotFoundException extends ResourceNotFoundException {

    public FareRuleNotFoundException(Long id) {
        super(
                ErrorCode.FARE_RULE_NOT_FOUND,
                "Fare Rule with id '" + id + "' was not found."
        );
    }
}