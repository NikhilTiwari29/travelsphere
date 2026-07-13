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
     * Registers a new user and generates a JWT access token.
     *
     * Processing flow:
     *
     * 1. Check whether the email is already registered.
     * 2. Reject public SYSTEM_ADMIN registration.
     * 3. Create the User entity.
     * 4. Encode the raw password.
     * 5. Persist the user.
     * 6. Load Spring Security UserDetails.
     * 7. Create an authenticated Authentication object.
     * 8. Store Authentication in the SecurityContext.
     * 9. Generate JWT.
     * 10. Return authentication response.
     *
     * Transaction:
     *
     * A read-write transaction is required because a new User record
     * is inserted into the database.
     *
     * Any runtime persistence failure causes the transaction to roll back.
     *
     * @param req registration request containing user information
     * @return authentication response containing user details and JWT
     * @throws UserException when registration validation fails
     */
    @Override
    @Transactional
    public AuthResponse signup(UserDTO req) throws UserException {

        log.info(
                "User registration started: email={}, role={}",
                req.getEmail(),
                req.getRole()
        );


        /*
         * Check whether another user already exists with the same email.
         *
         * Email uniqueness should also be enforced through a database
         * UNIQUE constraint because an application-level check alone
         * cannot completely prevent concurrent duplicate registrations.
         */
        User existingUser = userRepository.findByEmail(req.getEmail());

        if (existingUser != null) {

            log.warn(
                    "User registration rejected: email already registered, email={}",
                    req.getEmail()
            );

            throw new UserException("Email already registered");
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

            throw new UserException(
                    "Cannot register as SYSTEM_ADMIN"
            );
        }


        /*
         * Create and populate the persistent User entity.
         *
         * The raw password is encoded before the entity is persisted.
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
         * Since registration represents the user's first successful
         * authentication, initialize lastLogin with the current timestamp.
         */
        newUser.setLastLogin(LocalDateTime.now());


        /*
         * Persist the new user.
         */
        User savedUser = userRepository.save(newUser);

        log.info(
                "User successfully persisted during registration: userId={}, email={}, role={}",
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );


        /*
         * Load UserDetails after persistence.
         *
         * This provides the authorities expected by Spring Security and
         * by JwtProvider when generating authorization claims.
         */
        UserDetails userDetails =
                customUserDetailsService.loadUserByUsername(
                        savedUser.getEmail()
                );


        /*
         * Create an authenticated Authentication object.
         *
         * Principal:
         * UserDetails containing authenticated user information.
         *
         * Credentials:
         * null because the password is no longer needed after successful
         * registration.
         *
         * Authorities:
         * User roles and permissions used for authorization and JWT claims.
         */
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );


        /*
         * Store Authentication in the current request thread's
         * Spring SecurityContext.
         */
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);


        /*
         * Generate the JWT only after successful user creation and
         * Authentication construction.
         *
         * The JWT value itself must never be logged.
         */
        String jwt = jwtProvider.generateToken(
                authentication,
                savedUser.getId()
        );


        /*
         * Build the response returned to the client.
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
     * 1. Validate email and password.
     * 2. Create authenticated Authentication object.
     * 3. Populate SecurityContext.
     * 4. Load the application User entity.
     * 5. Generate JWT.
     * 6. Update lastLogin timestamp.
     * 7. Return authentication response.
     *
     * Transaction:
     *
     * A read-write transaction is required because the method modifies
     * the user's lastLogin timestamp.
     *
     * @param email user email
     * @param password raw password supplied by the client
     * @return authentication response containing user details and JWT
     * @throws UserException when authentication fails
     */
    @Override
    @Transactional
    public AuthResponse login(
            String email,
            String password) throws UserException {

        log.info(
                "User login attempt started: email={}",
                email
        );


        /*
         * Validate credentials and create an authenticated
         * Authentication object.
         */
        Authentication authentication =
                authenticate(email, password);


        /*
         * Store the authenticated identity in the current
         * Spring SecurityContext.
         */
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);


        /*
         * Load the domain User entity required for:
         *
         * - user ID
         * - profile information
         * - lastLogin update
         */
        User user = userRepository.findByEmail(email);

        /*
         * Defensive check.
         *
         * Normally authenticate() has already loaded the same user through
         * CustomUserDetailsService. This protects against unexpected
         * inconsistencies in the authentication/user lookup flow.
         */
        if (user == null) {

            log.error(
                    "Authenticated principal has no matching User entity: email={}",
                    email
            );

            throw new UserException("User not found");
        }


        /*
         * Generate JWT for the successfully authenticated user.
         *
         * The token value is intentionally not logged.
         */
        String token = jwtProvider.generateToken(
                authentication,
                user.getId()
        );


        /*
         * Update the timestamp of the latest successful login.
         *
         * Since the User entity is managed inside the current transaction,
         * JPA dirty checking can persist this change automatically at commit.
         *
         * An explicit userRepository.save(user) is therefore not required
         * when the entity was loaded and remains managed in this transaction.
         */
        user.setLastLogin(LocalDateTime.now());


        /*
         * Build the authentication response.
         */
        AuthResponse response = new AuthResponse();

        response.setTitle(
                "Login successful"
        );

        response.setMessage(
                "Welcome back " + user.getFullName()
        );

        response.setJwt(token);

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


    // ============================================================
    // CREDENTIAL AUTHENTICATION
    // ============================================================

    /**
     * Validates the supplied email and password.
     *
     * Processing flow:
     *
     * 1. Load UserDetails using the email.
     * 2. Compare the raw password with the stored encoded password.
     * 3. Reject invalid credentials.
     * 4. Create an authenticated Authentication object.
     *
     * Transaction behavior:
     *
     * This method is private and is called from transactional login().
     * Therefore, it participates in the transaction already active for
     * the login operation.
     *
     * @param email user email
     * @param password raw password supplied by the client
     * @return authenticated Spring Security Authentication object
     * @throws UserException when password validation fails
     */
    private Authentication authenticate(
            String email,
            String password) throws UserException {

        log.debug(
                "Validating login credentials: email={}",
                email
        );


        /*
         * Load the user through Spring Security's UserDetailsService.
         *
         * UserDetails contains:
         *
         * - username
         * - encoded password
         * - authorities
         */
        UserDetails userDetails =
                customUserDetailsService.loadUserByUsername(email);


        /*
         * Compare:
         *
         * raw password from login request
         *
         * against
         *
         * encoded password stored in the database.
         *
         * Password values must never be logged.
         */
        if (!passwordEncoder.matches(
                password,
                userDetails.getPassword())) {

            log.warn(
                    "Login authentication failed: invalid credentials, email={}",
                    email
            );

            throw new UserException(
                    "Invalid email or password"
            );
        }


        log.debug(
                "Login credentials validated successfully: email={}",
                email
        );


        /*
         * Return an authenticated token containing:
         *
         * Principal:
         * UserDetails
         *
         * Credentials:
         * null because credentials should not remain in memory longer
         * than necessary.
         *
         * Authorities:
         * roles and permissions loaded from the user account.
         */
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}