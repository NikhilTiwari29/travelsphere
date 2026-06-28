package com.nikhil.services.controller;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import com.nikhil.common_lib.payload.request.LoginRequest;
import com.nikhil.common_lib.payload.response.AuthResponse;
import com.nikhil.services.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @RequestBody @Valid UserDTO req) throws UserException {
        AuthResponse response = authService.signup(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody @Valid LoginRequest req) throws UserException {
        AuthResponse response = authService.login(req.getEmail(), req.getPassword());
        return ResponseEntity.ok(response);
    }
}
