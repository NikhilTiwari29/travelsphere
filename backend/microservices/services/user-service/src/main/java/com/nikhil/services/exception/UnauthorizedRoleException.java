package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ForbiddenException;

/**
 * Thrown when a user attempts to perform an operation
 * using a role that is not permitted.
 */
public class UnauthorizedRoleException extends ForbiddenException {

    public UnauthorizedRoleException(String role) {
        super(
                ErrorCode.FORBIDDEN,
                "Registration using role '" + role + "' is not permitted."
        );
    }
}