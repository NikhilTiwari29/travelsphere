package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class FareRuleAlreadyExistException extends ConflictException {
    public FareRuleAlreadyExistException(Long id) {
        super(
                ErrorCode.FARE_RULE_ALREADY_EXISTS,
                "Fare Rule with ID '" + id + "' already exists."
        );
    }
}
