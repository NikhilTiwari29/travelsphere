package com.nikhil.services.model;

import com.nikhil.common_lib.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "passengers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    @Email
    private String email;

    private String phone;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(unique = true)
    private String passportNumber;

    private String nationality;
    private String frequentFlyerNumber;

    // Cross-service ref: User (user-service)
    @Column(name = "primary_user_id")
    private Long primaryUserId;

    private Boolean requiresWheelchairAssistance = false;
    private String dietaryPreferences;
    private String medicalConditions;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public int getAge() {

        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }

    public boolean isAdult() {
        return getAge() >= 18;
    }

    @PrePersist
    @PreUpdate
    private void normalizeData() {
        this.email = email != null ? email.toLowerCase().trim() : null;
        this.firstName = capitalizeFirstLetter(firstName);
        this.lastName = capitalizeFirstLetter(lastName);
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
