package com.nikhil.services.exception;

import com.nikhil.common_lib.exception.BadRequestException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            FlightNotFoundException.class,
            FlightInstanceNotFoundException.class,
            AircraftNotFoundException.class,
            AirlineNotFoundException.class,
            ResourceNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(
            RuntimeException exception
    ) {

        log.error(exception.getMessage(), exception);

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(exception.getMessage())
                                .build()
                );
    }


    @ExceptionHandler({
            FlightAlreadyExistsException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflictException(
            RuntimeException exception
    ) {

        log.error(exception.getMessage(), exception);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(exception.getMessage())
                                .build()
                );
    }


    @ExceptionHandler({
            BadRequestException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(
            RuntimeException exception
    ) {

        log.error(exception.getMessage(), exception);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(exception.getMessage())
                                .build()
                );
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception
    ) {

        log.error("Unexpected exception", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message("Internal server error.")
                                .build()
                );
    }
}