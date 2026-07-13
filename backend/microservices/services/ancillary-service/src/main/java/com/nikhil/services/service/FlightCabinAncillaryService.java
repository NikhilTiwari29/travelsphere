package com.nikhil.services.service;

import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightCabinAncillaryRequest;
import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;

import java.util.List;

public interface FlightCabinAncillaryService {

    FlightCabinAncillaryResponse create(FlightCabinAncillaryRequest request) throws ResourceNotFoundException;

    List<FlightCabinAncillaryResponse> bulkCreate(List<FlightCabinAncillaryRequest> requests) throws ResourceNotFoundException;

    FlightCabinAncillaryResponse getById(Long id) throws ResourceNotFoundException;

    List<FlightCabinAncillaryResponse> getAllByFlightAndCabinClass(
            Long flightId, Long cabinClassId);

    List<FlightCabinAncillaryResponse> getAllByIds(List<Long> ids);
    FlightCabinAncillaryResponse getByFlightIdAndCabinClassAndType(
            Long flightId, Long cabinClassId, AncillaryType type) throws ResourceNotFoundException;

    List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassAndType(
            Long flightId, Long cabinClassId, AncillaryType type) throws ResourceNotFoundException;

    FlightCabinAncillaryResponse update(Long id, FlightCabinAncillaryRequest request) throws ResourceNotFoundException;

    void delete(Long id) throws ResourceNotFoundException;

    Double calculateAncillaryPrice(List<Long> ancillaryIds);
}
