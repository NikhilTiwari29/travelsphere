package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializationComponent implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;



    @Override
    public void run(String... args) {
        initializeAdminUser();
    }

    private void initializeAdminUser() {
        String adminUsername = "codewithnikhil@gmail.com";

        if (userRepository.findByEmail(adminUsername)==null) {
            User adminUser = new User();

            adminUser.setPassword(passwordEncoder.encode("codewithnikhil"));
            adminUser.setFullName("nikhil");
            adminUser.setEmail(adminUsername);
            adminUser.setRole(UserRole.ROLE_SYSTEM_ADMIN);

            User admin=userRepository.save(adminUser);
        }
    }
}
