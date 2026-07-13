package com.nikhil.common_lib.payload.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityResponse {

    private Long id;
    private String name;
    private String cityCode;
    private String countryCode;
    private String countryName;
    private String regionCode;
    private String timeZoneOffset;
}
