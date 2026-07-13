package com.nikhil.services.repository;

import com.nikhil.common_lib.enums.UserRole;
import com.nikhil.services.model.User;
import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
@Observed
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
