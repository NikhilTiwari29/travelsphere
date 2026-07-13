package com.nikhil.services.config;

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
     * Purpose: Create signed JWT for authenticated user.
     * Called By: AuthServiceImpl login/signup
     * Flow: authorities → claims (email, roles, userId) → sign → compact token
     */
    public String generateToken(Authentication auth, Long userId) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        String roles = populateAuthorities(authorities);

        return Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
                .claim("email", auth.getName())
                .claim("authorities", roles)
                .claim("userId", userId)
                .signWith(key)
                .compact();
    }

    public String getEmailFromJwtToken(String jwt) {
        if (jwt.startsWith(JwtConstant.TOKEN_PREFIX)) {
            jwt = jwt.substring(JwtConstant.TOKEN_PREFIX.length());
        }
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(jwt).getPayload();
        return String.valueOf(claims.get("email"));
    }

    private String populateAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<String> auths = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            auths.add(authority.getAuthority());
        }
        return String.join(",", auths);
    }
}
