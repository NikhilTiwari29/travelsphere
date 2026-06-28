package com.nikhil.services.service;

import com.nikhil.common_lib.payload.request.SeatRequest;
import com.nikhil.common_lib.payload.response.SeatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatService {


    void generateSeats(Long seatMapId) throws Exception;
    SeatResponse getSeatById(Long id);
    List<SeatResponse> getAll();
    SeatResponse updateSeat(Long id, SeatRequest request);

}
