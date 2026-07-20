package com.nikhil.common_lib.exception;

import com.nikhil.common_lib.enums.ErrorCode;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}