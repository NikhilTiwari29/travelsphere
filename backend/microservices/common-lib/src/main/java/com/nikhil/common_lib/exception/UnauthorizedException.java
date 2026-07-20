package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}