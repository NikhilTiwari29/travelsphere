package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.services.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * Lookup user by unique email. Used by AuthServiceImpl (login/signup),
     * CustomUserDetailsService, and DataInitializationComponent.
     */
    User findByEmail(String email);

    /*
     * Find all users with a given role. Supports admin or batch queries
     * that need users filtered by UserRole (e.g. ROLE_SYSTEM_ADMIN).
     */
    Set<User> findByRole(UserRole role);
}
