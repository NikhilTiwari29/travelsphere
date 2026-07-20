package com.nikhil.services.service.impl;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.common_lib.exception.UnauthorizedException;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.response.AuthResponse;
import com.nikhil.services.config.JwtProvider;
import com.nikhil.services.exception.InvalidCredentialsException;
import com.nikhil.services.exception.UnauthorizedRoleException;
import com.nikhil.services.exception.UserAlreadyExistsException;
import com.nikhil.services.exception.UserNotFoundException;
import com.nikhil.services.mapper.UserMapper;
import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import com.nikhil.services.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service implementation responsible for user authentication and registration.
 *
 * Main responsibilities:
 *
 * 1. Register new users.
 * 2. Prevent duplicate email registration.
 * 3. Prevent public SYSTEM_ADMIN registration.
 * 4. Encode user passwords before persistence.
 * 5. Validate login credentials.
 * 6. Create Spring Security Authentication objects.
 * 7. Populate the current SecurityContext.
 * 8. Generate JWT access tokens.
 * 9. Update the user's last successful login timestamp.
 *
 *
 * AUTHENTICATION FLOW
 * -------------------
 *
 * Signup:
 *
 * AuthController
 *      ↓
 * signup()
 *      ↓
 * Validate email uniqueness
 *      ↓
 * Validate requested role
 *      ↓
 * Encode password
 *      ↓
 * Save User
 *      ↓
 * Load UserDetails
 *      ↓
 * Create Authentication
 *      ↓
 * Populate SecurityContext
 *      ↓
 * Generate JWT
 *      ↓
 * Return AuthResponse
 *
 *
 * Login:
 *
 * AuthController
 *      ↓
 * login()
 *      ↓
 * authenticate()
 *      ↓
 * Load UserDetails
 *      ↓
 * Verify BCrypt password
 *      ↓
 * Create authenticated Authentication
 *      ↓
 * Populate SecurityContext
 *      ↓
 * Generate JWT
 *      ↓
 * Update lastLogin
 *      ↓
 * Return AuthResponse
 *
 *
 * TRANSACTION STRATEGY
 * --------------------
 *
 * Class level:
 *
 * @Transactional(readOnly = true)
 *
 * This establishes read-only behavior as the default transaction policy.
 *
 * Write methods:
 *
 * signup() → @Transactional
 * login()  → @Transactional
 *
 * Both methods modify persistent User state and therefore require
 * read-write transactions.
 *
 * authenticate() is a private helper and executes inside the transaction
 * started by login().
 *
 *
 * SECURITY LOGGING POLICY
 * -----------------------
 *
 * The following values must never be written to logs:
 *
 * - Raw passwords
 * - Encoded password hashes
 * - JWT access tokens
 * - Authorization headers
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    /**
     * Repository used for User persistence and email-based lookup.
     */
    private final UserRepository userRepository;

    /**
     * Password encoder used to:
     *
     * - hash passwords during registration
     * - verify raw login passwords against stored hashes
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Generates JWT access tokens after successful authentication.
     */
    private final JwtProvider jwtProvider;

    /**
     * Loads Spring Security UserDetails including credentials and authorities.
     */
    private final CustomUserDetailsService customUserDetailsService;


    // ============================================================
// SIGNUP
// ============================================================

    /**
     * Registers a new user and immediately returns a JWT access token.
     *
     * Processing flow:
     *
     * 1. Verify that the email is not already registered.
     * 2. Reject public SYSTEM_ADMIN registration.
     * 3. Create the User entity.
     * 4. Encode the raw password.
     * 5. Persist the user.
     * 6. Generate a JWT for the newly registered user.
     * 7. Return the authentication response.
     *
     * Architecture:
     *
     * This service is responsible for user registration and JWT issuance.
     * Since the application uses stateless JWT authentication, it does not
     * create a Spring Security Authentication or populate the SecurityContext.
     *
     * The returned JWT is presented by the client on subsequent requests.
     * The API Gateway validates the JWT, authenticates the request, performs
     * authorization, and forwards the trusted user identity to downstream
     * microservices.
     *
     * Transaction:
     *
     * A read-write transaction is required because a new User record is
     * inserted into the database.
     *
     * Any runtime persistence failure causes the transaction to roll back.
     *
     * @param req registration request containing user information
     * @return authentication response containing the registered user and JWT
     * @throws UserException when registration validation fails
     */
    @Override
    @Transactional
    public AuthResponse signup(UserDTO req) {

        log.info(
                "User registration started: email={}, role={}",
                req.getEmail(),
                req.getRole()
        );

        /*
         * Check whether another user already exists with the same email.
         *
         * Email uniqueness is also enforced through a database
         * UNIQUE constraint because an application-level check alone
         * cannot completely prevent concurrent duplicate registrations.
         */
        User existingUser = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UserNotFoundException(req.getEmail()));;

        if (existingUser != null) {

            log.warn(
                    "User registration rejected: email already registered, email={}",
                    req.getEmail()
            );

            throw new UserAlreadyExistsException(
                    existingUser.getEmail()
            );
        }

        /*
         * Prevent public registration of SYSTEM_ADMIN accounts.
         *
         * SYSTEM_ADMIN accounts should be provisioned through a controlled
         * administrative process instead of the public signup endpoint.
         */
        if (req.getRole() == UserRole.ROLE_SYSTEM_ADMIN) {

            log.warn(
                    "User registration rejected: SYSTEM_ADMIN registration attempted, email={}",
                    req.getEmail()
            );

            throw new UnauthorizedRoleException(req.getRole().name());
        }

        /*
         * Create and populate the User entity.
         *
         * The raw password is encoded before the entity is saved to
         * ensure that plaintext passwords are never stored.
         */
        User newUser = new User();

        newUser.setEmail(req.getEmail());

        newUser.setPassword(
                passwordEncoder.encode(req.getPassword())
        );

        newUser.setPhone(req.getPhone());
        newUser.setFullName(req.getFullName());
        newUser.setRole(req.getRole());

        /*
         * Initialize the user's last login timestamp.
         *
         * Since this application immediately issues a JWT after successful
         * registration, the registration time is also treated as the user's
         * first successful login.
         */
        newUser.setLastLogin(LocalDateTime.now());

        /*
         * Save the new user.
         */
        User savedUser = userRepository.save(newUser);

        log.info(
                "User successfully registered: userId={}, email={}, role={}",
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );

        /*
         * Generate a JWT for the newly registered user.
         *
         * The client will include this JWT in the Authorization header of
         * future requests. The API Gateway validates the JWT, authenticates
         * the request, performs authorization, and forwards the trusted
         * user identity to downstream microservices.
         */
        String jwt = jwtProvider.generateToken(savedUser);

        /*
         * Build the authentication response.
         */
        AuthResponse response = new AuthResponse();

        response.setTitle(
                "Welcome " + savedUser.getFullName()
        );

        response.setMessage(
                "Registration successful"
        );

        response.setUser(
                UserMapper.toDTO(savedUser)
        );

        response.setJwt(jwt);

        log.info(
                "User registration completed successfully: userId={}, email={}",
                savedUser.getId(),
                savedUser.getEmail()
        );

        return response;
    }


    // ============================================================
    // LOGIN
    // ============================================================

    /**
     * Authenticates an existing user and generates a JWT access token.
     *
     * Processing flow:
     *
     * 1. Validate the supplied email and password.
     * 2. Generate a JWT for the authenticated user.
     * 3. Update the user's last successful login timestamp.
     * 4. Return the authentication response.
     *
     * Architecture:
     *
     * This service validates user credentials and issues a JWT.
     * Authentication and authorization of subsequent requests are
     * performed by the API Gateway by validating the JWT.
     *
     * Since the application uses stateless JWT authentication,
     * no SecurityContext is created or maintained in this service.
     *
     * Transaction:
     *
     * A read-write transaction is required because the method updates
     * the user's lastLogin timestamp.
     *
     * @param email user email
     * @param password raw password supplied by the client
     * @return authentication response containing the authenticated user and JWT
     * @throws UserException when authentication fails
     */

    @Override
    @Transactional
    public AuthResponse login(
            String email,
            String password) {

        log.info(
                "User login attempt started: email={}",
                email
        );

        /*
         * Authenticate the supplied credentials.
         *
         * This validates the email and password and returns the
         * authenticated User entity.
         */
        User user = authenticate(email, password);

        /*
         * Generate a JWT for the authenticated user.
         *
         * The client will include this token in the Authorization header
         * of every subsequent request.
         *
         * Authentication and authorization for protected requests are
         * performed by the API Gateway after validating the JWT.
         */
        String jwt = jwtProvider.generateToken(user);

        /*
         * Update the timestamp of the latest successful login.
         *
         * Since the User entity is managed by the current persistence
         * context, JPA dirty checking will automatically persist this
         * change when the transaction commits.
         */
        user.setLastLogin(LocalDateTime.now());

        /*
         * Build the authentication response.
         */
        AuthResponse response = new AuthResponse();

        response.setTitle("Login successful");

        response.setMessage(
                "Welcome back " + user.getFullName()
        );

        response.setJwt(jwt);

        response.setUser(
                UserMapper.toDTO(user)
        );

        log.info(
                "User login completed successfully: userId={}, email={}, role={}",
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return response;
    }


    private User authenticate(
            String email,
            String password) {

        log.debug(
                "Validating login credentials: email={}",
                email
        );

        /*
         * Load the user from the database.
         */
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));;

        if (user == null) {

            log.warn(
                    "Login authentication failed: user not found, email={}",
                    email
            );

            /*
             * Do not reveal whether the email or password is incorrect.
             * Returning a generic authentication failure prevents user
             * enumeration attacks.
             */
            throw new InvalidCredentialsException();
        }

        /*
         * Compare the raw password supplied by the client with the
         * encoded password stored in the database.
         *
         * Password values must never be logged.
         */
        if (!passwordEncoder.matches(
                password,
                user.getPassword())) {

            log.warn(
                    "Login authentication failed: invalid credentials, email={}",
                    email
            );

            /*
             * Return the same exception for both invalid email and password
             * to avoid leaking account existence.
             */
            throw new InvalidCredentialsException();
        }

        log.debug(
                "Login credentials validated successfully: email={}",
                email
        );

        return user;
    }
}