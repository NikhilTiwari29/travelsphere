package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.*;
import com.nikhil.common_lib.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                ex,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                ex,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                ex,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                ex,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(
                ErrorResponse.validation(
                        "Validation failed.",
                        request.getRequestURI(),
                        errors
                )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        ErrorCode.VALIDATION_FAILED,
                        ex.getMessage(),
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Requested resource was not found.",
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred.",
                        request.getRequestURI()
                )
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            BaseException ex,
            String path) {

        return ResponseEntity.status(status).body(
                ErrorResponse.of(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        path
                )
        );
    }
}