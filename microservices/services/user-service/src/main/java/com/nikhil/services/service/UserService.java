package com.nikhil.services.service;

import com.nikhil.common_lib.exception.UserException;
import com.nikhil.services.model.User;

import java.util.List;

/*
 * User domain service contract for read operations (profile, lookup, list).
 *
 * Implemented by UserServiceImpl. Invoked by UserController on
 * gateway-protected /api/users/** routes after JWT validation.
 */
public interface UserService {
    User getUserByEmail(String email) throws UserException;
    User getUserById(Long id) throws UserException;
    List<User> getUsers() throws UserException;
}
