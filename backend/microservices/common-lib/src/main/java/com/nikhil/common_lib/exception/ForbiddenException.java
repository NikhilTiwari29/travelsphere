package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;

public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}