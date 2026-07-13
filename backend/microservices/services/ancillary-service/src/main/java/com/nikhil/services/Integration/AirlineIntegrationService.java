package com.nikhil.services.Integration;

import com.nikhil.common_lib.payload.response.AircraftResponse;

public interface AirlineIntegrationService {
    Long getAirlineIdForUser(Long userId);
    AircraftResponse getAircraftById(Long aircraftId);
}
