package com.nikhil.services.Integration.impl;

import com.nikhil.common_lib.payload.response.AircraftResponse;
import com.nikhil.services.Integration.AirlineIntegrationService;
import com.nikhil.services.client.AirlineClient;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AirlineIntegrationServiceImpl implements AirlineIntegrationService {

    private final AirlineClient airlineClient;

    @Override
    public Long getAirlineIdForUser(Long userId) {
        try {
            return airlineClient.getAirlineByOwner(userId).getId();
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("No airline found for user: " + userId);
        } catch (FeignException e) {
            throw new RuntimeException("Airline service error: " + e.getMessage(), e);
        }
    }

    @Override
    public AircraftResponse getAircraftById(Long aircraftId) {
        try {
            return airlineClient.getAircraftById(aircraftId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("No aircraft found for id: " + aircraftId);
        } catch (FeignException e) {
            throw new RuntimeException("Aircraft service error: " + e.getMessage(), e);
        }
    }
}
