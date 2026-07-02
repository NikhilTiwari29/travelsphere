package com.nikhil.services.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Simple health/welcome endpoint at service root (/).
 *
 * Useful for direct service checks (bypassing Gateway) during
 * local development or Eureka instance verification.
 */
@RestController
public class HomeController {

    @GetMapping
    public ResponseEntity<?> HomeTest() {
        return ResponseEntity.ok("Welcome to user service!");
    }
}
