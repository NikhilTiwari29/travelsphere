package com.nikhil.services.service;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.PassengerRequest;
import com.nikhil.common_lib.payload.response.PassengerResponse;
import com.nikhil.services.model.Passenger;

public interface PassengerService {

    PassengerResponse createPassenger(PassengerRequest request, Long userId)
            throws ResourceNotFoundException;

    Passenger findOrCreatePassengerEntity(PassengerRequest request, Long userId);

    Passenger findExistingPassenger(PassengerRequest request);

    boolean existsById(Long id);

    long count();
}
