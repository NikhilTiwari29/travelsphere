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

    @NotBlank
    @Size(min = 1, max = 5)
    private String code;

    @Size(max = 500)
    private String description;

    @NotNull
    private Long aircraftId;

    private Integer displayOrder;
    private Boolean isActive;
    private Boolean isBookable;
    private Integer typicalSeatPitch;
    private Integer typicalSeatWidth;
    private String seatType;
}
