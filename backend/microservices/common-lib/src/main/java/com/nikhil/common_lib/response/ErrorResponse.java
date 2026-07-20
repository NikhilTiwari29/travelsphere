package com.nikhil.common_lib.response;

import com.nikhil.common_lib.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response returned by all microservices.
 */
@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {

    /**
     * Indicates that the request failed.
     */
    @Builder.Default
    private final boolean success = false;

    /**
     * Application-specific error code.
     */
    private final ErrorCode errorCode;

    /**
     * Human-readable error message.
     */
    private final String message;

    /**
     * Request path.
     */
    private final String path;

    /**
     * Validation errors (field -> message).
     * Null for non-validation exceptions.
     */
    private final Map<String, String> errors;

    /**
     * Response timestamp.
     */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Creates an error response.
     */
    public static ErrorResponse of(
            ErrorCode errorCode,
            String message,
            String path
    ) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .build();
    }

    /**
     * Creates a validation error response.
     */
    public static ErrorResponse validation(
            String message,
            String path,
            Map<String, String> errors
    ) {
        return ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .message(message)
                .path(path)
                .errors(errors)
                .build();
    }
}