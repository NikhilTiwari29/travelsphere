package com.nikhil.services.mapper;

import com.nikhil.common_lib.payload.response.PaymentDTO;
import com.nikhil.services.model.Payment;

import java.time.ZoneId;

/*
 * Maps Payment entity fields to the shared PaymentDTO returned by REST and Feign.
 * Keeps service/controller layers free of manual field copying.
 */
public class PaymentMapper {

    /** Converts a persisted Payment to API payload; null-safe on input and timestamps. */
    public static PaymentDTO toDTO(Payment payment) {
        if (payment == null) {
            return null;
        }

        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setGateway(payment.getProvider());
        dto.setAmount(payment.getAmount() != null ? payment.getAmount().longValue() : null);
        dto.setTransactionId(payment.getTransactionId());
        dto.setPaymentMethod(payment.getMethod());
        dto.setStatus(payment.getStatus());
        dto.setUserId(payment.getUserId());
        dto.setBookingId(payment.getBookingId());

        if (payment.getPaidAt() != null) {
            dto.setCompletedAt(payment.getPaidAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        if (payment.getCreatedAt() != null) {
            dto.setCreatedAt(payment.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
            dto.setInitiatedAt(payment.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        if (payment.getUpdatedAt() != null) {
            dto.setUpdatedAt(payment.getUpdatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        return dto;
    }
}
