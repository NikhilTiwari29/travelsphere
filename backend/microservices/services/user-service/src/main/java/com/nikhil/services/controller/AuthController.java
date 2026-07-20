package com.nikhil.services.controller;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.request.LoginRequest;
import com.nikhil.common_lib.payload.response.AuthResponse;
import com.nikhil.common_lib.response.ApiResponse;
import com.nikhil.services.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Public REST controller responsible for user registration and authentication.
 *
 * <p>This controller belongs to {@code user-service}. Clients access these
 * endpoints through the API Gateway using the {@code /auth/**} route.</p>
 *
 * <p>Because signup and login are public authentication operations, the client
 * does not need an existing JWT to call these endpoints.</p>
 *
 * <p>Request flow:</p>
 *
 * <pre>
 * Client
 *    |
 *    v
 * API Gateway
 *    |
 *    | /auth/**
 *    v
 * AuthController
 *    |
 *    v
 * AuthService
 *    |
 *    +---- User registration / credential verification
 *    |
 *    +---- JWT generation
 *    |
 *    v
 * AuthResponse
 * </pre>
 *
 * <p>Security considerations:</p>
 *
 * <ul>
 *     <li>Passwords must never be written to application logs.</li>
 *     <li>JWT access tokens must never be written to application logs.</li>
 *     <li>Authentication logic remains in the service layer.</li>
 *     <li>The controller is responsible only for HTTP request handling,
 *         validation, delegation, and response construction.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Authentication service containing signup and login business logic.
     */
    private final AuthService authService;


    /**
     * Registers a new user account and returns an authentication response.
     *
     * <p>The request body is validated before the service method is invoked.
     * The authentication service is responsible for creating the user,
     * assigning the appropriate role, persisting the account, and generating
     * the JWT.</p>
     *
     * <p>Request flow:</p>
     *
     * <pre>
     * POST /auth/signup
     *          |
     *          v
     * Bean Validation
     *          |
     *          v
     * AuthService.signup()
     *          |
     *          +---- Validate account uniqueness
     *          |
     *          +---- Create user
     *          |
     *          +---- Generate JWT
     *          |
     *          v
     * AuthResponse
     * </pre>
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @RequestBody @Valid UserDTO request
    ) throws UserException {

        log.info(
                "Received user signup request"
        );

        AuthResponse response =
                authService.signup(request);

        log.info(
                "User signup completed successfully"
        );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "User registered successfully.",
                                response
                        )
                );
    }


    /**
     * Authenticates an existing user and returns an authentication response.
     *
     * <p>The request body is validated before authentication begins.
     * Credential verification and JWT generation are delegated to the
     * authentication service.</p>
     *
     * <p>Request flow:</p>
     *
     * <pre>
     * POST /auth/login
     *          |
     *          v
     * Bean Validation
     *          |
     *          v
     * AuthService.login()
     *          |
     *          +---- Load user account
     *          |
     *          +---- Verify credentials
     *          |
     *          +---- Generate JWT
     *          |
     *          +---- Update last login
     *          |
     *          v
     * AuthResponse
     * </pre>
     *
     * @param request validated login credentials
     * @return authentication response containing the JWT and authenticated
     *         user information
     * @throws UserException when authentication fails
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request
    ) throws UserException {

        /*
         * Log only the authentication event.
         *
         * Passwords and other credentials must never be included in logs.
         *
         * Since distributed tracing is being introduced, request correlation
         * should eventually rely on traceId/spanId rather than logging
         * credentials or other user-specific values.
         */
        log.info(
                "Received user login request"
        );


        /*
         * Delegate credential verification and JWT generation to the
         * authentication service.
         */
        AuthResponse response =
                authService.login(
                        request.getEmail(),
                        request.getPassword()
                );


        /*
         * Reaching this point means authentication and response generation
         * completed successfully.
         *
         * Authentication failures are propagated to the centralized exception
         * handling layer and therefore do not produce this success message.
         */
        log.info(
                "User authentication completed successfully"
        );



        return ResponseEntity.ok(
                ApiResponse.success(
                        "User authenticated successfully.",
                        response
                )
        );
    }
}