package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;

public class BadRequestException extends BaseException {

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}