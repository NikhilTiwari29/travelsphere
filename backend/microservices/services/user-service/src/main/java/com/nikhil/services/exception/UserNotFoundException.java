package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

/**
 * Thrown when a user cannot be found.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Long userId) {
        super(
                ErrorCode.USER_NOT_FOUND,
                "User with id '" + userId + "' was not found."
        );
    }

    public UserNotFoundException(String email) {
        super(
                ErrorCode.USER_NOT_FOUND,
                "User with email '" + email + "' was not found."
        );
    }
}