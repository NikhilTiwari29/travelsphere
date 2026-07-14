package com.nikhil.services.config;

import com.nikhil.services.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/*
 * JWT token creation for authenticated users after login/signup.
 *
 * Architecture Fit
 * ----------------
 * Signs tokens here; api-gateway JwtUtil validates them using the same
 * JwtConstant.SECRET_KEY on protected routes (/api/users/**, etc.).
 *
 * Token Claims: email, authorities (roles), userId, issuedAt, expiration (24h)
 */
@Service
public class JwtProvider {

    private final SecretKey key = Keys.hmacShaKeyFor(
            JwtConstant.SECRET_KEY.getBytes());

    /*
     * Generates a signed JWT containing the authenticated user's identity
     * and authorization claims. The API Gateway validates this token to
     * authenticate and authorize subsequent requests.
     */
    public String generateToken(User user) {

        return Jwts.builder()
                .issuedAt(new Date())
                /*
                    Expiration time is 1 day or 24 hours
                 */
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .claim("email", user.getEmail())
                .claim("userId", user.getId())
                /*
                 * Store the role as its enum name.
                 */
                .claim("authorities", user.getRole().name())
                .signWith(key)
                .compact();
    }
}
