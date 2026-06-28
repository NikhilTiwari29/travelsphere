package com.nikhil.services.service;


import com.nikhil.common_lib.payload.request.FareRequest;
import com.nikhil.common_lib.payload.response.FareResponse;
import com.nikhil.services.model.Fare;


import java.util.List;
import java.util.Map;

public interface FareService {

    FareResponse createFare(FareRequest request);
    List<FareResponse> createFares(List<FareRequest> requests);
    FareResponse getFareById(Long id);
    List<FareResponse> getFaresByFlightIdAndCabinClassId(
            Long flightId,
            Long cabinClassId
    );
    FareResponse updateFare(
            Long id,
            FareRequest request
    );
    void deleteFare(Long id);

//    FareResponse getFareByIdWithDetails(Long id);
//    List<FareResponse> getFaresByFlightId(Long flightId);
//    List<FareResponse> getFaresByFlightIdWithDetails(Long flightId);


    List<Fare> getFares();

    /**
     * Returns the cheapest fare per flight for the given cabin class.
     * Used by flight-ops-service to apply price range filtering during search
     * without a cross-service join.
     *
     * @param flightIds  flight IDs to query (matches FlightInstance.flight.id)
     * @param cabinClassId the requested cabin class
     * @return map of flightId → cheapest FareResponse for that cabin class;
     *         flights with no fare for the given cabin class are absent from the map
     */
    Map<Long, FareResponse> getLowestFarePerFlight(
            List<Long> flightIds, Long cabinClassId);

    FareResponse getLowestFareForFlightAndCabin(Long flightId, Long cabinClassId);

    Map<Long, FareResponse> getFaresByIds(List<Long> ids);
}
