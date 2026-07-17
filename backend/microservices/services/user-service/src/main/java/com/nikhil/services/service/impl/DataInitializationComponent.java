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
        initializeAdminUsers();
    }

    /**
     * Creates default SYSTEM_ADMIN users if they do not already exist.
     */
    private void initializeAdminUsers() {

        String[][] admins = {
                {"nikhiltiwari@gmail.com", "Nikhil Tiwari"},
                {"indigo.admin@travelsphere.com", "IndiGo Admin"},
                {"airindia.admin@travelsphere.com", "Air India Admin"},
                {"emirates.admin@travelsphere.com", "Emirates Admin"},
                {"qatar.admin@travelsphere.com", "Qatar Airways Admin"},
                {"singapore.admin@travelsphere.com", "Singapore Airlines Admin"},
                {"lufthansa.admin@travelsphere.com", "Lufthansa Admin"},
                {"british.admin@travelsphere.com", "British Airways Admin"},
                {"american.admin@travelsphere.com", "American Airlines Admin"},
                {"delta.admin@travelsphere.com", "Delta Air Lines Admin"}
        };

        for (String[] admin : admins) {

            String email = admin[0];
            String fullName = admin[1];

            if (userRepository.findByEmail(email) == null) {

                User user = new User();
                user.setEmail(email);
                user.setFullName(fullName);
                user.setPassword(passwordEncoder.encode("Password@123"));
                user.setRole(UserRole.ROLE_SYSTEM_ADMIN);

                userRepository.save(user);
            }
        }
    }
}
