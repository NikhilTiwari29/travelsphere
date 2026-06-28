package com.nikhil.common_lib.payload.response;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSummaryResponse {
    private Long id;
    private String bookingReference;
    private String status;
    private Double totalAmount;
    private Instant bookingDate;
}
