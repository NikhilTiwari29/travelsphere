package com.nikhil.services.clients;

import com.nikhil.common_lib.payload.request.PaymentInitiateRequest;
import com.nikhil.common_lib.payload.response.PaymentDTO;
import com.nikhil.common_lib.payload.response.PaymentInitiateResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PaymentClientFallback implements PaymentClient {

    @Override
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request, Long userId) {
        return null;
    }

    @Override
    public PaymentDTO getPaymentByBookingId(Long bookingId) {
        return null;
    }

    @Override
    public Map<Long, PaymentDTO> getPaymentsByBookingIds(List<Long> bookingIds) {
        return Collections.emptyMap();
    }
}
