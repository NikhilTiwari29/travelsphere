package com.nikhil.services.controller;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.services.mapper.UserMapper;
import com.nikhil.services.model.User;
import com.nikhil.services.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Protected user profile and lookup REST endpoints.
 *
 * Architecture Fit
 * ----------------
 * Gateway RouteConfig.userServiceRoutes() forwards /api/users/** here
 * after jwtAuthFilter validates JWT and injects X-User-* headers.
 *
 * Request Flow
 * ------------
 * Client (Bearer JWT) → Gateway (JWT check) → UserController → UserService
 *
 * Profile endpoint reads X-User-Email set by the Gateway, not the JWT directly.
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /*
     * Purpose: Return the authenticated user's profile.
     * Called By: Client via Gateway GET /api/users/profile
     * Flow: Read X-User-Email header → UserService → UserMapper.toDTO
     */
    @GetMapping("/api/users/profile")
    public ResponseEntity<UserDTO> getUserProfile(
            @RequestHeader("X-User-Email") String email) throws UserException {
        User user = userService.getUserByEmail(email);
        return ResponseEntity.ok(UserMapper.toDTO(user));
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<UserDTO> getUserById(
            @PathVariable Long userId) throws UserException {
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserMapper.toDTO(user));
    }

    @GetMapping("/api/users")
    public ResponseEntity<List<UserDTO>> getUsers() throws UserException {
        List<User> users = userService.getUsers();
        return ResponseEntity.ok(UserMapper.toDTOList(users));
    }
}
