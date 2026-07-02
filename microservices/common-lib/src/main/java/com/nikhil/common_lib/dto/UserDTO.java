package com.nikhil.common_lib.dto;

import com.nikhil.common_lib.enums.UserRole;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shared user representation exchanged between microservices via Feign clients.
 *
 * Produced by user-service; consumed by booking-service and payment-service for
 * ownership checks and notification personalization. Password is omitted in the
 * safe constructor used for inter-service calls.
 */
@Data
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String email;
    private String password;
    private String phone;
    private String fullName;
    private UserRole role;
    private LocalDateTime lastLogin;

    public UserDTO(Long id, String email, String fullName,
                   UserRole role, LocalDateTime lastLogin) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.password = null;
        this.phone = null;
        this.lastLogin = lastLogin;
    }
}
