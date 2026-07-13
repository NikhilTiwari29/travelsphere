package com.nikhil.common_lib.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityRequest {

    @NotBlank(message = "City name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "City code is required")
    @Size(max = 10)
    private String cityCode;

    @NotBlank(message = "Country code is required")
    @Size(max = 5)
    private String countryCode;

    @NotBlank(message = "Country name is required")
    @Size(max = 100)
    private String countryName;

    @Size(max = 10)
    private String regionCode;

    @Size(max = 10)
    private String timeZoneOffset;
}
