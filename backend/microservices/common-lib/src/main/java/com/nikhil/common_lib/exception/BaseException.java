package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import lombok.Getter;

/**
 * Base class for all application-specific exceptions.
 *
 * Every custom exception carries:
 * - An application-specific error code
 * - A human-readable error message
 *
 * Concrete exceptions should extend one of the semantic exceptions
 * (BadRequestException, ResourceNotFoundException, ConflictException, etc.)
 * rather than extending this class directly.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}