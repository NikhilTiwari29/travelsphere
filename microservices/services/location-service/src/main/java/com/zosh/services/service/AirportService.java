package com.nikhil.services.service;

import com.nikhil.common_lib.exception.AirportException;
import com.nikhil.common_lib.exception.CityException;
import com.nikhil.common_lib.payload.request.AirportRequest;
import com.nikhil.common_lib.payload.response.AirportResponse;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;

public interface AirportService {

    AirportResponse createAirport(AirportRequest request) throws AirportException, CityException;
    List<AirportResponse> createBulkAirports(List<AirportRequest> requests) throws AirportException, CityException;
    AirportResponse getAirportById(Long id);

    List<AirportResponse> getAllAirports();
    AirportResponse updateAirport(Long id, AirportRequest request) throws AirportException, CityException;
    void deleteAirport(Long id) throws AirportException;
    List<AirportResponse> getAirportsByCityId(Long cityId);
}
