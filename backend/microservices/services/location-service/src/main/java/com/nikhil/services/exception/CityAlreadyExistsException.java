package com.nikhil.services.exception;

import com.nikhil.common_lib.enums.ErrorCode;
import com.nikhil.common_lib.exception.ConflictException;

public class CityAlreadyExistsException extends ConflictException {

    public CityAlreadyExistsException(String cityCode) {
        super(
                ErrorCode.CITY_ALREADY_EXISTS,
                "City with code '" + cityCode + "' already exists."
        );
    }
}