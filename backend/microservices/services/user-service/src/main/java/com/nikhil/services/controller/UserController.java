package com.nikhil.services.controller;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.mapper.UserMapper;
import com.nikhil.services.model.User;
import com.nikhil.services.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /**
     * Returns the authenticated user's profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(
            @RequestHeader("X-User-Email") String email) throws UserException {

        log.info("Fetching authenticated user profile.");

        User user = userService.getUserByEmail(email);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User profile retrieved successfully.",
                        UserMapper.toDTO(user)
                )
        );
    }

    /**
     * Returns a user by id.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(
            @PathVariable Long userId) throws UserException {

        log.info("Fetching user by id={}", userId);

        User user = userService.getUserById(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User retrieved successfully.",
                        UserMapper.toDTO(user)
                )
        );
    }

    /**
     * Returns all users.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getUsers() throws UserException {

        log.info("Fetching all users.");

        List<User> users = userService.getUsers();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Users retrieved successfully.",
                        UserMapper.toDTOList(users)
                )
        );
    }
}