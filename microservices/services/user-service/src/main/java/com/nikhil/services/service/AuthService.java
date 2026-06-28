package com.nikhil.services.service;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.response.AuthResponse;

public interface AuthService {
    AuthResponse login(String email, String password) throws UserException;
    AuthResponse signup(UserDTO req) throws UserException;
}
