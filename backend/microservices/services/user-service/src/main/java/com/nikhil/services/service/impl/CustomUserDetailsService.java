package com.nikhil.services.service.impl;

import com.nikhil.services.model.User;
import com.nikhil.services.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

/*
 * Spring Security UserDetailsService adapter for credential lookup.
 *
 * Called By: AuthServiceImpl.authenticate() during login.
 * Flow: findByEmail → map UserRole to GrantedAuthority → Spring UserDetails.
 *
 * Does not validate passwords; AuthServiceImpl uses PasswordEncoder
 * after loading user details through this service.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /*
     * Purpose: Load user by email for Spring Security authentication.
     * Called By: AuthServiceImpl.authenticate()
     * Flow: UserRepository.findByEmail → build UserDetails with role authority
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }

        GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().toString());
        Collection<GrantedAuthority> authorities = Collections
                .singletonList(authority);

        /*
         * Convert our application's User entity into Spring Security's UserDetails
         * implementation so Spring Security knows the username, password, and
         * authorities to use during authentication to avoid complexity of handling System own User class
         *  since every system will have their Own User logic
         */
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                authorities
        );
    }
}
