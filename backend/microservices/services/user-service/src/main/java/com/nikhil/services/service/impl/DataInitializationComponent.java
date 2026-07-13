package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/*
 * Startup seed data: ensures a default SYSTEM_ADMIN exists.
 *
 * Runs once via CommandLineRunner after Spring context is ready.
 * Skips creation if the admin email is already registered in the database.
 */
@Component
@RequiredArgsConstructor
public class DataInitializationComponent implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;



    @Override
    public void run(String... args) {
        initializeAdminUser();
    }

    /*
     * Purpose: Create default SYSTEM_ADMIN if not present.
     * Called By: run() on application startup
     * Flow: findByEmail → if absent → encode password → save admin user
     */
    private void initializeAdminUser() {
        String adminUsername = "nikhiltiwarip29@gmail.com";

        if (userRepository.findByEmail(adminUsername)==null) {
            User adminUser = new User();

            adminUser.setPassword(passwordEncoder.encode("NikTiwari@1234"));
            adminUser.setFullName("nikhil");
            adminUser.setEmail(adminUsername);
            adminUser.setRole(UserRole.ROLE_SYSTEM_ADMIN);

            User admin=userRepository.save(adminUser);
        }
    }
}
