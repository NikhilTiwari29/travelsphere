package com.nikhil.services.service.impl;

import com.nikhil.services.exception.UserNotFoundException;
import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import com.nikhil.services.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * User read/query operations backed by UserRepository.
 *
 * Called by UserController after the Gateway has authenticated the
 * client and attached identity headers to the forwarded request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User getUserByEmail(String email) {

        log.debug("Fetching user by email={}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    @Override
    public User getUserById(Long id) {

        log.debug("Fetching user by id={}", id);

        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public List<User> getUsers() {

        log.debug("Fetching all users");

        return userRepository.findAll();
    }
}