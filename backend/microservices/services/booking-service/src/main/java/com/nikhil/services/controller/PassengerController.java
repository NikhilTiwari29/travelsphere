package com.nikhil.services.controller;

import com.nikhil.services.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/*
 * Passenger HTTP surface (currently empty). Passenger entities are created
 * internally by BookingServiceImpl via PassengerService during booking.
 */
@RestController
@RequestMapping("/api/passengers")
@RequiredArgsConstructor
public class PassengerController {

    private final PassengerService passengerService;
}
