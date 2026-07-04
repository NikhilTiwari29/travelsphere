package com.nikhil.services.service.impl;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.response.AuthResponse;
import com.nikhil.services.config.JwtProvider;
import com.nikhil.services.mapper.UserMapper;
import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import com.nikhil.services.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * Core authentication business logic: credential validation, user
 * registration, JWT issuance, and SecurityContext population.
 *
 * Architecture Fit
 * ----------------
 * Called by AuthController on /auth/** routes. Issues JWTs that the
 * Gateway later validates on protected routes (/api/users/**, etc.).
 *
 * JWT Flow (login/signup)
 * -----------------------
 * Validate/create user → build Authentication → JwtProvider.generateToken
 * → return AuthResponse with JWT to client
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // Repository for performing CRUD operations on User entity
    private final UserRepository userRepository;

    // BCrypt password encoder used to hash passwords and verify credentials
    private final PasswordEncoder passwordEncoder;

    // Generates JWT tokens after successful authentication
    private final JwtProvider jwtProvider;

    // Loads user details required for authentication
    private final CustomUserDetailsService customUserDetailsService;

    /*
     * Purpose: Register a new user and return a JWT.
     * Called By: AuthController.signup()
     * Flow: duplicate check → encode password → save → Authentication → JWT
     */
    @Override
    public AuthResponse signup(UserDTO req) throws UserException {

        // Check for duplicate email
        User existingUser = userRepository.findByEmail(req.getEmail());
        if (existingUser != null) {
            throw new UserException("Email already registered");
        }

        // Prevent users from creating SYSTEM_ADMIN accounts
        if (req.getRole() == UserRole.ROLE_SYSTEM_ADMIN) {
            throw new UserException("Cannot register as SYSTEM_ADMIN");
        }

        // Create and populate the User entity
        User newUserCreation = new User();
        newUserCreation.setEmail(req.getEmail());
        newUserCreation.setPassword(passwordEncoder.encode(req.getPassword()));
        newUserCreation.setPhone(req.getPhone());
        newUserCreation.setFullName(req.getFullName());
        newUserCreation.setRole(req.getRole());
        newUserCreation.setLastLogin(LocalDateTime.now());

        // Persist the user
        User savedUser = userRepository.save(newUserCreation);

        /*
         * Create a UsernamePasswordAuthenticationToken because Spring Security
         * represents username/password-based authentication using this Authentication
         * implementation. It stores the user's identity and authentication details
         * in a format understood by Spring Security.
         */
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        savedUser.getEmail(),
                        savedUser.getPassword()
                );

        // Store authentication in the current SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate JWT containing authenticated user's information
        String jwt = jwtProvider.generateToken(authentication, savedUser.getId());

        // Build response
        AuthResponse response = new AuthResponse();
        response.setTitle("Welcome " + savedUser.getFullName());
        response.setMessage("Registration successful");
        response.setUser(UserMapper.toDTO(savedUser));
        response.setJwt(jwt);

        return response;
    }

    /*
     * Purpose: Authenticate credentials and return a JWT.
     * Called By: AuthController.login()
     * Flow: authenticate → SecurityContext → JWT → update lastLogin
     */
    @Override
    public AuthResponse login(String email, String password) throws UserException {

        // Authenticate user credentials
        Authentication authentication = authenticate(email, password);

        // Store authenticated user in Spring Security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(email);

        // Generate JWT for authenticated user
        String token = jwtProvider.generateToken(authentication, user.getId());

        // Record the latest successful login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Build response
        AuthResponse response = new AuthResponse();
        response.setTitle("Login successful");
        response.setMessage("Welcome back " + user.getFullName());
        response.setJwt(token);
        response.setUser(UserMapper.toDTO(user));

        return response;
    }

    /*
     * Purpose: Verify email/password against stored BCrypt hash.
     * Called By: login()
     * Flow: CustomUserDetailsService.loadUserByUsername → passwordEncoder.matches
     */
    private Authentication authenticate(String email, String password) throws UserException {

        // Load user from database through UserDetailsService
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // Compare raw password with the stored BCrypt hash
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new UserException("Invalid password");
        }

        /*
         * Create an authenticated Authentication object.
         * Credentials are set to null because they are no longer needed
         * after successful authentication.
         */
        return new UsernamePasswordAuthenticationToken(
                email,
                null,
                userDetails.getAuthorities()
        );
    }
}