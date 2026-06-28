package com.nikhil.services.service;

import com.nikhil.common_lib.payload.request.SeatMapRequest;
import com.nikhil.common_lib.payload.response.SeatMapResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatMapService {

    SeatMapResponse createSeatMap(Long userId, SeatMapRequest request) throws Exception;
    List<SeatMapResponse> createSeatMaps(Long userId, List<SeatMapRequest> requests) throws Exception;
    SeatMapResponse getSeatMapById(Long id);

    SeatMapResponse getSeatMapsByCabinClass(Long cabinClassId);
    SeatMapResponse updateSeatMap(Long userId, Long id, SeatMapRequest request);
    void deleteSeatMap(Long id) throws Exception;
}
