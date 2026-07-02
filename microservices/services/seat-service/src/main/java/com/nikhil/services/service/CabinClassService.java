package com.nikhil.services.service;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.CabinClassRequest;
import com.nikhil.common_lib.payload.response.CabinClassResponse;

import java.util.List;

/*
 * Service contract for cabin-class CRUD on an aircraft layout.
 *
 * Backed by CabinClassController (/api/cabin-classes/**).
 * flight-ops-service SeatClient calls getByAircraftIdAndName during search.
 * Each CabinClass owns one SeatMap in the entity hierarchy.
 */
public interface CabinClassService {

    CabinClassResponse createCabinClass(CabinClassRequest request);
    List<CabinClassResponse> createCabinClasses(List<CabinClassRequest> requests);
    CabinClassResponse getCabinClassById(Long id);
    List<CabinClassResponse> getCabinClassesByAircraftId(
                    Long aircraftId);
    CabinClassResponse getByAircraftIdAndName(Long aircraftId,
                                              CabinClassType name);
    CabinClassResponse updateCabinClass(Long id, CabinClassRequest request);
    void deleteCabinClass(Long id);
}
