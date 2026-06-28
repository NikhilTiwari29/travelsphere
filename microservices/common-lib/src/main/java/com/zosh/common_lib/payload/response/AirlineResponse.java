package com.nikhil.common_lib.payload.response;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.embeddable.Support;
import com.nikhil.common_lib.enums.AirlineStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineResponse {

    private Long id;

    private String iataCode;
    private String icaoCode;

    private String name;
    private String alias;
    private String country;

    private String logoUrl;
    private String website;

    private AirlineStatus status;
    private String alliance;

//    private Long baggagePolicyId;

    private Long headquartersCityId;
//    private String headquartersCityName;
//
//    private String supportEmail;
//    private String supportPhone;
//    private String supportHours;

    private Instant createdAt;
    private Instant updatedAt;

    private Long ownerId;
    private UserDTO owner;
    private Long updatedById;

//    private CityResponse headquartersCity;
    private Support support;
}
