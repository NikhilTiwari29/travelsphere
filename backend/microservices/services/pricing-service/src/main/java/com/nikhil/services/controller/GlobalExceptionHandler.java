package com.nikhil.services.controller;

import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.exception.FareAlreadyExistsException;
import com.nikhil.services.exception.FareNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FareNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFareNotFoundException(
            FareNotFoundException exception
    ) {

        log.error("Fare not found", exception);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(exception.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(FareAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleFareAlreadyExistsException(
            FareAlreadyExistsException exception
    ) {

        log.error("Fare already exists", exception);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(exception.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception
    ) {

        log.error("Unexpected exception", exception);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("Internal server error.")
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}