package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.response.SeatInstanceResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SeatClientFallback implements SeatClient {

    @Override
    public Double calculateSeatPrice(List<Long> seatInstanceIds) {
        return 0.0;
    }

    @Override
    public List<SeatInstanceResponse> getAllByIds(List<Long> Ids) {
        return Collections.emptyList();
    }
}
