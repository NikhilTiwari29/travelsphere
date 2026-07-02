package com.nikhil.services.service;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.response.AuthResponse;

/*
 * Contract for authentication operations: login and signup.
 *
 * Implemented by AuthServiceImpl. Called by AuthController after the
 * Gateway forwards public /auth/** requests to user-service.
 */
public interface AuthService {
    AuthResponse login(String email, String password) throws UserException;
    AuthResponse signup(UserDTO req) throws UserException;
}
