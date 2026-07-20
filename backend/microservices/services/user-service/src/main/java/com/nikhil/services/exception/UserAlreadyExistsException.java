package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

/**
 * Thrown when attempting to register a user with an email
 * that already exists.
 */
public class UserAlreadyExistsException extends ConflictException {

    public UserAlreadyExistsException(String email) {
        super(
                ErrorCode.EMAIL_ALREADY_EXISTS,
                "A user with email '" + email + "' already exists."
        );
    }
}