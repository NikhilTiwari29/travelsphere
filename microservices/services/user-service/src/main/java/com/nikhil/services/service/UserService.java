package com.nikhil.services.service;

import com.nikhil.common_lib.exception.UserException;
import com.nikhil.services.model.User;

import java.util.List;

public interface UserService {
    User getUserByEmail(String email) throws UserException;
    User getUserById(Long id) throws UserException;
    List<User> getUsers() throws UserException;
}
