package com.nikhil.common_lib.payload.request;

import com.nikhil.common_lib.embeddable.ContactInfo;
import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.enums.TripType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request body for creating a new flight booking.
 *
 * Sent from the client via API Gateway to booking-service. Captures flight/cabin/fare
 * selection, passengers, optional ancillaries/meals, seat numbers, and contact info.
 * A PENDING booking is created; confirmation follows after payment via Kafka events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Flight Instance ID is required")
    private Long flightInstanceId;

    @NotNull(message = "Cabin class is required")
    private CabinClassType cabinClass;

//    @NotNull(message = "Trip type is required")
    private TripType tripType;

    @NotNull(message = "Fare ID is required")
    private Long fareId;

    @NotNull(message = "At least one passenger is required")
    @Size(min = 1, message = "At least one passenger is required")
    private List<PassengerRequest> passengers;

    private ContactInfo contactInfo;

    private List<Long> ancillaryIds;
    private List<Long> mealIds;

    private String promoCode;

    private List<String> seatNumbers;
}
