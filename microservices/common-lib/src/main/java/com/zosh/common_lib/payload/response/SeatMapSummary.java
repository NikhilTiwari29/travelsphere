package com.nikhil.common_lib.payload.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapSummary {
    private int totalRows;
    private int rightSeatsPerRow;
    private int leftSeatsPerRow;
}
