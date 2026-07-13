package com.nikhil.common_lib.payload.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinClassRequest {

    @NotBlank(message = "Name is required")
    private String name;

    // Short airline/booking code used to identify the cabin class,
    // e.g. Y for Economy, W for Premium Economy, J for Business, F for First Class.
    @NotBlank(message = "Cabin class code is required")
    @Size(
            min = 1,
            max = 5,
            message = "Cabin class code must be between 1 and 5 characters"
    )
    private String code;

    @Size(max = 500)
    private String description;

    // ID of the aircraft whose physical cabin layout contains this cabin class.
    @NotNull
    private Long aircraftId;

    // Controls the UI display sequence of cabin classes, e.g. First → Business → Premium Economy → Economy.
    private Integer displayOrder;

    // Indicates whether this cabin-class configuration is currently enabled for operational use.
    private Boolean isActive;

    // Indicates whether passengers are currently allowed to make bookings in this cabin class.
    private Boolean isBookable;

    // Typical distance between the same reference point on consecutive seat rows, measured in inches.
    private Integer typicalSeatPitch;

    // Typical seat cushion width, measured in inches.
    private Integer typicalSeatWidth;

    // Describes the physical seat style, e.g. STANDARD, RECLINER, LIE_FLAT, or SUITE.
    private String seatType;
}