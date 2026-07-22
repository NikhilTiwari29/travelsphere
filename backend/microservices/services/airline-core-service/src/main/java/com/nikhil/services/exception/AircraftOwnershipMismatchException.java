package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ForbiddenException;

public class AircraftOwnershipMismatchException extends ForbiddenException {

    public AircraftOwnershipMismatchException() {
        super(
                ErrorCode.AIRCRAFT_OWNERSHIP_MISMATCH,
                "Aircraft does not belong to the authenticated airline."
        );
    }
}