package com.nikhil.services.service;


import com.nikhil.common_lib.payload.request.FlightSearchRequest;
import com.nikhil.common_lib.payload.response.FlightInstanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface FlightSearchService {

    Page<FlightInstanceResponse> searchFlights(
            FlightSearchRequest request,
            Pageable pageable
    );

}
