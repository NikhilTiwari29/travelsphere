package com.nikhil.common_lib.dto;

import com.nikhil.common_lib.enums.UserRole;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100,
            message = "Password must be between 8 and 100 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Phone number must be a valid 10-digit mobile number"
    )
    private String phone;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100,
            message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotNull(message = "User role is required")
    private UserRole role;

    private LocalDateTime lastLogin;

    public UserDTO(Long id,
                   String email,
                   String fullName,
                   UserRole role,
                   LocalDateTime lastLogin) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.password = null;
        this.phone = null;
        this.lastLogin = lastLogin;
    }
}
