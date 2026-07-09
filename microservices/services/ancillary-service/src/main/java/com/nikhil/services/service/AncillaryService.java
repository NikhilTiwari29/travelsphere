package com.nikhil.services.service;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.AncillaryRequest;
import com.nikhil.common_lib.payload.response.AncillaryResponse;

import java.util.List;

public interface AncillaryService {

    AncillaryResponse create(Long userId, AncillaryRequest request) throws ResourceNotFoundException;

    AncillaryResponse getById(Long id) throws ResourceNotFoundException;

    List<AncillaryResponse> getAllByAirlineId(Long userId);

    AncillaryResponse update(Long id, AncillaryRequest request) throws ResourceNotFoundException;

    void delete(Long id) throws ResourceNotFoundException;
}
