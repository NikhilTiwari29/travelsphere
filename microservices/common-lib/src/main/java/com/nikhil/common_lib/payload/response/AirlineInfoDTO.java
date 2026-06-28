package com.nikhil.common_lib.payload.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineInfoDTO {

    private String name;
    private String logo;
    private String iataCode;
}
