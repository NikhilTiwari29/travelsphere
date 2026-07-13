package com.nikhil.services.service;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.BookingRequest;
import com.nikhil.common_lib.payload.response.*;

import java.util.List;

public interface BookingService {

    PaymentInitiateResponse createBooking(BookingRequest request, Long userId)
            throws ResourceNotFoundException, PaymentException;

    BookingResponse updateBooking(Long id, BookingRequest request)
            throws ResourceNotFoundException;

    BookingResponse getBookingById(Long id) throws ResourceNotFoundException;



    List<BookingResponse> getBookingsByAirline(
            Long userId,
            String searchQuery,
            BookingStatus status,
            Long flightInstanceId,
            String sortDirection
    );

    List<BookingResponse> getBookingsByUser(Long userId);

    BookingResponse cancelBooking(Long id) throws ResourceNotFoundException;

    void deleteBooking(Long id) throws ResourceNotFoundException;

    boolean existsById(Long id);

    long count();

    long countByFlightId(Long flightId);

    BookingStatisticsResponse getBookingStatisticsForAirline(Long airlineId);
}
