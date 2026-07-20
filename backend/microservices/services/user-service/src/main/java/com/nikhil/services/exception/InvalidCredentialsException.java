package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.UnauthorizedException;

/**
 * Thrown when the supplied login credentials are invalid.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super(
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid email or password."
        );
    }
}