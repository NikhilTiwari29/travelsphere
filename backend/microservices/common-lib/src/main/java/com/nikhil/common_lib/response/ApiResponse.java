package com.nikhil.common_lib.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response returned by all successful endpoints.
 *
 * @param <T> the type of response payload
 */
@Getter
@Builder
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * Indicates whether the request was successful.
     */
    private final boolean success;

    /**
     * Human-readable success message.
     */
    private final String message;

    /**
     * Response payload.
     */
    private final T data;

    /**
     * Response timestamp.
     */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Creates a success response with data.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Creates a success response without data.
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }
}