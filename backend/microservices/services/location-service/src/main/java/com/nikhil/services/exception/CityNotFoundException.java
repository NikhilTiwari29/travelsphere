package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ResourceNotFoundException;

/**
 * Thrown when the requested city does not exist.
 */
public class CityNotFoundException extends ResourceNotFoundException {

    public CityNotFoundException(Long cityId) {
        super(
                ErrorCode.CITY_NOT_FOUND,
                "City with id '" + cityId + "' was not found."
        );
    }
}