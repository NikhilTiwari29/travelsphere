package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;

public class ConflictException extends BaseException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}