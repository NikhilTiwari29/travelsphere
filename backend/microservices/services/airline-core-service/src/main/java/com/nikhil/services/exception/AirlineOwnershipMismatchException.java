package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ForbiddenException;

public class AirlineOwnershipMismatchException extends ForbiddenException {

    public AirlineOwnershipMismatchException() {
        super(
                ErrorCode.AIRLINE_OWNERSHIP_MISMATCH,
                "The airline does not belong to the authenticated owner."
        );
    }
}